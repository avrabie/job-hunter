import {
  apiFetch,
  ApiError,
  isApiPath,
  type TokenSource,
} from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

export type FileKind = 'CV' | 'OTHER'

/** What the backend says about one stored file; never its content. */
export interface FileInfo {
  id: string
  name: string
  kind: FileKind
  contentType: string
  sizeBytes: number
  uploadedAt: string
  master: boolean
}

/** The file picker of a CV upload; the server checks the type again. */
export const ACCEPTED_CV_TYPES = '.pdf,.md,.txt'

export type ListResult =
  | { kind: 'loaded'; files: FileInfo[] }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type UploadResult =
  | { kind: 'stored'; file: FileInfo }
  | { kind: 'cancelled' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type ActionResult =
  | { kind: 'done' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/files'

/** Fixed texts per status. Results never carry a response body, a file name from the server, or file content. */
const UPLOAD_MESSAGES: Record<FileKind, Record<number, string>> = {
  CV: {
    400: 'The file must be a PDF, a Markdown file or a plain text file.',
    413: 'The file is larger than 5 MB.',
  },
  OTHER: {
    413: 'The file is larger than 10 GB.',
  },
}

const UPLOAD_COMMON: Record<number, string> = {
  507: 'The server has no room for this file right now.',
}

const MASTER_MESSAGES: Record<number, string> = {
  400: 'Only a CV file can be the master CV.',
  404: 'That file no longer exists.',
}

/** What the browser answers for one upload attempt, before it is turned into a result. */
type Sent =
  | { kind: 'answer'; status: number; text: string }
  | { kind: 'cancelled' }
  | { kind: 'unavailable' }

async function guarded<T extends { kind: string }>(
  run: () => Promise<T>,
): Promise<T | { kind: 'unauthorized' } | { kind: 'failed'; message: string }> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

/** A 404 means the deployment has no file endpoint yet: the cabinet is empty, as the single-CV endpoint was. */
export function listFiles(auth: TokenSource): Promise<ListResult> {
  return guarded<ListResult>(async () => {
    const res = await apiFetch(auth, PATH)
    if (res.status === 404) return { kind: 'loaded', files: [] }
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', files: (await res.json()) as FileInfo[] }
  })
}

export function deleteFile(
  auth: TokenSource,
  id: string,
): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'done' }
  })
}

export function setMaster(
  auth: TokenSource,
  id: string,
): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/master`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fileId: id }),
    })
    if (!res.ok)
      return {
        kind: 'failed',
        message: MASTER_MESSAGES[res.status] ?? messageForStatus(res.status),
      }
    return { kind: 'done' }
  })
}

export function clearMaster(auth: TokenSource): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/master`, { method: 'DELETE' })
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'done' }
  })
}

/**
 * Asks for a single-use ticket and points the browser at the file. The file is an attachment, so the page stays and
 * the bytes never go through page memory.
 */
export function requestDownload(
  auth: TokenSource,
  id: string,
): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(
      auth,
      `${PATH}/${encodeURIComponent(id)}/download-ticket`,
      { method: 'POST' },
    )
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    const { path } = (await res.json()) as { path?: unknown }
    // Only our own file path, so the page never navigates anywhere else.
    if (
      typeof path !== 'string' ||
      !isApiPath(path) ||
      !path.startsWith(`${PATH}/`)
    )
      return { kind: 'failed', message: messageForError(new Error()) }
    window.location.assign(path)
    return { kind: 'done' }
  })
}

/**
 * Uploads with `XMLHttpRequest`, because `fetch` cannot report progress. `onProgress` gets a fraction from 0 to 1. Aborting
 * the signal cancels the request. One 401 renews the token and repeats the upload once.
 */
export function uploadFile(
  auth: TokenSource,
  file: File,
  kind: FileKind,
  onProgress: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<UploadResult> {
  const body = new FormData()
  body.append('kind', kind)
  body.append('file', file)
  return uploadWithRenewal(auth, body, kind, onProgress, signal)
}

async function uploadWithRenewal(
  auth: TokenSource,
  body: FormData,
  kind: FileKind,
  onProgress: (fraction: number) => void,
  signal: AbortSignal | undefined,
): Promise<UploadResult> {
  const token = auth.getAccessToken()
  if (!token) return { kind: 'unauthorized' }

  const first = await sendUpload(token, body, onProgress, signal)
  if (first.kind !== 'answer' || first.status !== 401)
    return toUploadResult(first, kind)

  const renewed = await auth.renew()
  if (!renewed) return signedOut(auth)
  const second = await sendUpload(renewed, body, onProgress, signal)
  if (second.kind === 'answer' && second.status === 401) return signedOut(auth)
  return toUploadResult(second, kind)
}

async function signedOut(auth: TokenSource): Promise<UploadResult> {
  await auth.discard()
  return { kind: 'unauthorized' }
}

function sendUpload(
  token: string,
  body: FormData,
  onProgress: (fraction: number) => void,
  signal: AbortSignal | undefined,
): Promise<Sent> {
  return new Promise((resolve) => {
    if (signal?.aborted) return resolve({ kind: 'cancelled' })
    const xhr = new XMLHttpRequest()
    xhr.open('POST', PATH)
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && e.total > 0) onProgress(e.loaded / e.total)
    }
    xhr.onload = () =>
      resolve({ kind: 'answer', status: xhr.status, text: xhr.responseText })
    xhr.onerror = () => resolve({ kind: 'unavailable' })
    xhr.onabort = () => resolve({ kind: 'cancelled' })
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(body)
  })
}

function toUploadResult(sent: Sent, kind: FileKind): UploadResult {
  if (sent.kind === 'cancelled') return sent
  if (sent.kind === 'unavailable')
    return {
      kind: 'failed',
      message: messageForError(new ApiError('unavailable')),
    }
  if (sent.status >= 200 && sent.status < 300) {
    try {
      return { kind: 'stored', file: JSON.parse(sent.text) as FileInfo }
    } catch {
      return { kind: 'failed', message: messageForError(new Error()) }
    }
  }
  return {
    kind: 'failed',
    message:
      UPLOAD_MESSAGES[kind][sent.status] ??
      UPLOAD_COMMON[sent.status] ??
      messageForStatus(sent.status),
  }
}
