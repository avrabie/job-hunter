import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

/** What the server read from a page, kept on the server until it is confirmed or expires. */
export interface JobDraft {
  draftId: string
  title: string
  company: string
  location: string
  description: string
  language: string
}

/** The fields the user may change before saving. */
export interface JobEdits {
  title: string
  company: string
  location: string
  description: string
}

/** The application and posting a confirmed import leads to. `created` is false when the user already tracked it. */
export interface ImportedJob {
  applicationId: string
  postingId: string
  created: boolean
  postingExisted: boolean
}

export const IMPORT_ERROR_CODES = [
  'invalid_url',
  'unsupported_site',
  'blocked_address',
  'unreachable',
  'not_html',
  'too_large',
  'unreadable',
  'not_a_job',
  'extraction_failed',
  'rate_limited',
  'llm_unavailable',
  'draft_not_found',
] as const

export type ImportErrorCode = (typeof IMPORT_ERROR_CODES)[number]

/** Fixed texts per error code. The response body is never shown. */
const CODE_MESSAGES: Record<ImportErrorCode, string> = {
  invalid_url:
    'That does not look like a web address. Check the URL and try again.',
  unsupported_site:
    "Jobs cannot be imported from this site. Use the company's own posting instead.",
  blocked_address:
    'This address cannot be read. Paste the job description instead.',
  unreachable:
    'The page could not be reached. Paste the job description instead.',
  not_html:
    'This link does not lead to a web page. Paste the job description instead.',
  too_large:
    'The page is too large to read. Paste the job description instead.',
  unreadable:
    'The job description could not be read from this page. Paste it instead.',
  not_a_job: 'This page does not seem to be a single job posting.',
  extraction_failed:
    'The job details could not be extracted from this page. Try again later.',
  rate_limited:
    'Too many imports in a short time. Wait a few minutes and try again.',
  llm_unavailable:
    'The import service is unavailable right now. Please try again later.',
  draft_not_found: 'This preview has expired. Preview the URL again.',
}

export type Unauthorized = { kind: 'unauthorized' }

/** `code` is null when the server sent no known code (network failure, other status). */
export type Failed = {
  kind: 'failed'
  code: ImportErrorCode | null
  message: string
}

export type PreviewResult =
  { kind: 'previewed'; draft: JobDraft } | Unauthorized | Failed

export type ConfirmResult =
  { kind: 'imported'; job: ImportedJob } | Unauthorized | Failed

const PREVIEW_PATH = '/api/job-imports/preview'
const PATH = '/api/job-imports'

function isImportErrorCode(value: unknown): value is ImportErrorCode {
  return (
    typeof value === 'string' &&
    (IMPORT_ERROR_CODES as readonly string[]).includes(value)
  )
}

/** The error code of a failed response, or null. Only the code is read; the rest of the body is dropped. */
async function errorCode(res: Response): Promise<ImportErrorCode | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    return isImportErrorCode(body.error) ? body.error : null
  } catch {
    return null
  }
}

async function failure(res: Response): Promise<Failed> {
  const code = await errorCode(res)
  return {
    kind: 'failed',
    code,
    message: code ? CODE_MESSAGES[code] : messageForStatus(res.status),
  }
}

async function guarded<T extends { kind: string }>(
  run: () => Promise<T>,
): Promise<T | Unauthorized | Failed> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', code: null, message: messageForError(e) }
  }
}

/**
 * Reads the posting at `url` and returns a draft for review. `text` is the description the user pasted; it is sent
 * only when the server could not read the page itself.
 */
export function previewJobImport(
  auth: TokenSource,
  input: { url: string; text?: string },
): Promise<PreviewResult> {
  return guarded<PreviewResult>(async () => {
    const res = await apiFetch(auth, PREVIEW_PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    if (!res.ok) return failure(res)
    return { kind: 'previewed', draft: (await res.json()) as JobDraft }
  })
}

/** Saves the reviewed draft as a posting the user tracks. `draft_not_found` means the draft expired. */
export function confirmJobImport(
  auth: TokenSource,
  draftId: string,
  edits: JobEdits,
): Promise<ConfirmResult> {
  return guarded<ConfirmResult>(async () => {
    const res = await apiFetch(auth, PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ draftId, ...edits }),
    })
    if (!res.ok) return failure(res)
    return { kind: 'imported', job: (await res.json()) as ImportedJob }
  })
}
