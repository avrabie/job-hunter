import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

export type LetterLanguage = 'EN' | 'DE'
export type LetterHow = 'GENERATED' | 'REGENERATED' | 'EDITED'

export interface LetterSummary {
  version: number
  how: LetterHow
  language: LetterLanguage
  createdAt: string
  basedOnVersion: number | null
}

export interface LetterComments {
  general: string | null
  paragraphs: { n: number; text: string }[]
}

export interface LetterSelection {
  experience: number[]
  projects: number[]
  skills: number[]
}

export interface LetterDetail extends LetterSummary {
  text: string
  selection: LetterSelection
  instructions: string | null
  comments: LetterComments | null
}

/** Indexes refer to the saved profile's lists; the server resolves them and never takes profile text. */
export interface GenerateRequest extends LetterSelection {
  language: LetterLanguage | null
  instructions: string | null
  /** Names of ticked skills the CV or projects evidence; never quotes or notes. */
  evidencedSkills?: string[]
  basedOnVersion: number | null
  comments: LetterComments | null
}

export type LetterErrorCode =
  | 'selection_invalid'
  | 'letter_text_invalid'
  | 'application_limit'
  | 'letter_limit'
  | 'letter_not_configured'
  | 'letter_failed'
  | 'rate_limited'

/** Fixed texts per error code; the response body is never shown. */
export const LETTER_MESSAGES: Record<LetterErrorCode, string> = {
  selection_invalid:
    'The chosen entries no longer match your saved profile. Reload the page and choose again.',
  letter_text_invalid:
    'The letter text is empty, longer than 6 000 characters or contains HTML. Please change it.',
  application_limit: 'You have reached the limit of 500 tracked jobs.',
  letter_limit:
    'This job already has 20 letter versions. Remove an old version first.',
  letter_not_configured:
    'The writing assistant is not available on this server.',
  letter_failed:
    'The writing assistant could not produce a usable letter. Please try again.',
  rate_limited:
    'You have reached the limit of letters for this hour. Please try again later.',
}

export type LetterResult =
  | { kind: 'done'; letter: LetterDetail }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; code?: LetterErrorCode | null; message: string }

export type LettersResult =
  | { kind: 'loaded'; letters: LetterSummary[] }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type LetterFileResult =
  | { kind: 'done'; blob: Blob; fileName: string }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type LetterActionResult =
  | { kind: 'done' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const base = (applicationId: string) =>
  `/api/applications/${encodeURIComponent(applicationId)}/letters`

const JSON_HEADERS = { 'Content-Type': 'application/json' }

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

async function errorCodeOf(res: Response): Promise<LetterErrorCode | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    if (typeof body.error === 'string' && body.error in LETTER_MESSAGES)
      return body.error as LetterErrorCode
  } catch {
    // No readable body: the status decides.
  }
  if (res.status === 429) return 'rate_limited'
  return null
}

async function letterResult(res: Response): Promise<LetterResult> {
  if (res.ok)
    return { kind: 'done', letter: (await res.json()) as LetterDetail }
  const code = await errorCodeOf(res)
  return {
    kind: 'failed',
    code,
    message: code ? LETTER_MESSAGES[code] : messageForStatus(res.status),
  }
}

/** Generates a first draft, or with `basedOnVersion` a regeneration. Counts against the hourly limit. */
export function generateLetter(
  auth: TokenSource,
  applicationId: string,
  request: GenerateRequest,
): Promise<LetterResult> {
  return guarded<LetterResult>(async () =>
    letterResult(
      await apiFetch(auth, base(applicationId), {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify(request),
      }),
    ),
  )
}

/** Saves the user's own text as a new version; no model call. */
export function saveEditedLetter(
  auth: TokenSource,
  applicationId: string,
  text: string,
): Promise<LetterResult> {
  return guarded<LetterResult>(async () =>
    letterResult(
      await apiFetch(auth, `${base(applicationId)}/edited`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ text }),
      }),
    ),
  )
}

export function listLetters(
  auth: TokenSource,
  applicationId: string,
): Promise<LettersResult> {
  return guarded<LettersResult>(async () => {
    const res = await apiFetch(auth, base(applicationId))
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', letters: (await res.json()) as LetterSummary[] }
  })
}

export function readLetter(
  auth: TokenSource,
  applicationId: string,
  version: number,
): Promise<LetterResult> {
  return guarded<LetterResult>(async () =>
    letterResult(await apiFetch(auth, `${base(applicationId)}/${version}`)),
  )
}

export function deleteLetter(
  auth: TokenSource,
  applicationId: string,
  version: number,
): Promise<LetterActionResult> {
  return guarded<LetterActionResult>(async () => {
    const res = await apiFetch(auth, `${base(applicationId)}/${version}`, {
      method: 'DELETE',
    })
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'done' }
  })
}

/** The Markdown bytes of one version and the server's plain file name (or a fallback). */
export function downloadLetter(
  auth: TokenSource,
  applicationId: string,
  version: number,
): Promise<LetterFileResult> {
  return guarded<LetterFileResult>(async () => {
    const res = await apiFetch(
      auth,
      `${base(applicationId)}/${version}/download`,
    )
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    const match = res.headers
      .get('Content-Disposition')
      ?.match(/filename="([A-Za-z0-9._-]{1,120})"/)
    return {
      kind: 'done',
      blob: await res.blob(),
      fileName: match?.[1] ?? 'motivation-letter.md',
    }
  })
}

/** The same version as an A4 PDF made by the server, and its plain file name (or a fallback). */
export function downloadLetterPdf(
  auth: TokenSource,
  applicationId: string,
  version: number,
): Promise<LetterFileResult> {
  return guarded<LetterFileResult>(async () => {
    const res = await apiFetch(auth, `${base(applicationId)}/${version}/pdf`)
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    const match = res.headers
      .get('Content-Disposition')
      ?.match(/filename="([A-Za-z0-9._-]{1,120})"/)
    return {
      kind: 'done',
      blob: await res.blob(),
      fileName: match?.[1] ?? 'motivation-letter.pdf',
    }
  })
}
