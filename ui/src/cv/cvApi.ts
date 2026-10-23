import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { emptyProfile, type Profile } from '../profile/types.ts'

/** What the backend says about the stored CV; never its content. */
export interface CvInfo {
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export const ACCEPTED_TYPES = '.pdf,.md,.txt'

export type CvResult =
  | { kind: 'loaded'; cv: CvInfo }
  | { kind: 'none' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type UploadResult =
  | { kind: 'stored'; cv: CvInfo }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type DraftResult =
  | { kind: 'draft'; profile: Profile }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type DownloadResult =
  | { kind: 'done' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type DeleteResult =
  | { kind: 'deleted' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/cv'

/** Fixed texts per status. Results never carry a response body, a file name from the server, or CV content. */
const UPLOAD_MESSAGES: Record<number, string> = {
  400: 'The file must be a PDF, a Markdown file or a plain text file.',
  413: 'The file is larger than 5 MB.',
}

const EXTRACT_MESSAGES: Record<number, string> = {
  404: 'Upload a CV first.',
  409: 'Choose a master CV in your files first.',
  413: 'The CV text is too long to read.',
  422: 'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
  429: 'Too many extractions. Please try again later.',
  502: 'The CV could not be read right now. Please try again.',
  503: 'Reading CVs is not set up on this server yet.',
}

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

export function loadCv(auth: TokenSource): Promise<CvResult> {
  return guarded<CvResult>(async () => {
    const res = await apiFetch(auth, PATH)
    if (res.status === 404) return { kind: 'none' }
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', cv: (await res.json()) as CvInfo }
  })
}

export function uploadCv(auth: TokenSource, file: File): Promise<UploadResult> {
  return guarded<UploadResult>(async () => {
    const body = new FormData()
    body.append('file', file)
    const res = await apiFetch(auth, PATH, { method: 'POST', body })
    if (!res.ok)
      return {
        kind: 'failed',
        message: UPLOAD_MESSAGES[res.status] ?? messageForStatus(res.status),
      }
    return { kind: 'stored', cv: (await res.json()) as CvInfo }
  })
}

export function deleteCv(auth: TokenSource): Promise<DeleteResult> {
  return guarded<DeleteResult>(async () => {
    const res = await apiFetch(auth, PATH, { method: 'DELETE' })
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'deleted' }
  })
}

/** Fetches the file with the user's token and hands it to the browser as a download under `fileName`. */
export function downloadCv(
  auth: TokenSource,
  fileName: string,
): Promise<DownloadResult> {
  return guarded<DownloadResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/file`)
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    const url = URL.createObjectURL(await res.blob())
    try {
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      link.click()
    } finally {
      URL.revokeObjectURL(url)
    }
    return { kind: 'done' }
  })
}

/** Asks the backend to read the stored CV. The draft is not saved anywhere by this call. */
export function extractProfile(auth: TokenSource): Promise<DraftResult> {
  return guarded<DraftResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/extract`, { method: 'POST' })
    if (!res.ok)
      return {
        kind: 'failed',
        message: EXTRACT_MESSAGES[res.status] ?? messageForStatus(res.status),
      }
    return { kind: 'draft', profile: withDefaults(await res.json()) }
  })
}

/** The draft with every field present, so a missing one cannot break the form. */
function withDefaults(draft: Partial<Profile>): Profile {
  const base = emptyProfile()
  return {
    ...base,
    ...draft,
    preferences: { ...base.preferences, ...draft.preferences },
  }
}

/**
 * The CV fills the facts it can know. The user's preferences (roles, workload, salary...) are not in a CV and stay as
 * they are.
 */
export function applyDraft(current: Profile, draft: Profile): Profile {
  return { ...draft, preferences: current.preferences }
}
