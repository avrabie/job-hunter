import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import type { PublicCvSettings } from './options.ts'

export type PublicCvErrorCode =
  | 'validation_failed'
  | 'handle_taken'
  | 'handle_reserved'
  | 'handle_held'
  | 'handle_change_too_soon'
  | 'name_required'
  | 'locked'
  | 'rate_limited'
  | 'photo_invalid'

export type PublicCvState =
  | { configured: false }
  | {
      configured: true
      handle: string
      published: boolean
      locked: boolean
      settings: PublicCvSettings
      /** When the handle may be changed again, or null when it may be changed now. */
      handleChangeableAt: string | null
      updatedAt: string
    }

export type HandleReason = 'invalid' | 'reserved' | 'taken' | 'held'
export type HandleCheck =
  { available: true } | { available: false; reason: HandleReason }

export type PublicCvResult<T> =
  | { kind: 'ok'; value: T }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; code: PublicCvErrorCode | null; message: string }

/** The body of `PUT /api/cv/public`. */
export interface PublicCvSave {
  handle: string
  published: boolean
  settings: PublicCvSettings
}

const PATH = '/api/cv/public'
const PREVIEW_PATH = '/api/cv/public/preview'
const PREVIEW_ANSWER = /^\/api\/cv\/public\/preview\/[A-Za-z0-9._~-]{1,200}$/

// Fixed texts for the error codes of the backend; the body is never shown.
const MESSAGES: Record<PublicCvErrorCode, string> = {
  validation_failed:
    'Use 3 to 30 characters: letters and digits, with single hyphens between them.',
  handle_taken: 'This handle is already taken. Choose another one.',
  handle_reserved: 'This handle is reserved. Choose another one.',
  handle_held:
    'This handle was used recently and cannot be used yet. Choose another one.',
  handle_change_too_soon: 'The handle can be changed only once every 30 days.',
  name_required:
    'Add your full name in the Contact section of your profile first.',
  locked: 'An administrator disabled this page. It cannot be published again.',
  rate_limited:
    'You have reached the limit for this hour. Please try again later.',
  photo_invalid:
    'The chosen photo cannot be used. Choose another JPEG file or "No photo".',
}

const REASON_MESSAGES: Record<HandleReason, string> = {
  invalid:
    'Use 3 to 30 characters: letters and digits, with single hyphens between them.',
  reserved: 'This handle is reserved. Choose another one.',
  taken: 'This handle is already taken.',
  held: 'This handle was used recently and cannot be used yet.',
}

/** The text for a reason the server gives when a handle is not available. */
export function handleReasonMessage(reason: HandleReason): string {
  return REASON_MESSAGES[reason]
}

/** Loads the owner's public page state (`configured: false` when there is none). */
export function getPublicCv(
  auth: TokenSource,
): Promise<PublicCvResult<PublicCvState>> {
  return call(async () => {
    const res = await apiFetch(auth, PATH)
    if (!res.ok) return failure(res)
    return { kind: 'ok', value: (await res.json()) as PublicCvState }
  })
}

/** Saves the handle, the settings and whether the page is published. Nothing is kept in the page or in storage. */
export function savePublicCv(
  auth: TokenSource,
  body: PublicCvSave,
): Promise<PublicCvResult<PublicCvState>> {
  return call(async () => {
    const res = await apiFetch(auth, PATH, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) return failure(res)
    return { kind: 'ok', value: (await res.json()) as PublicCvState }
  })
}

/** Removes the public page; the handle is held for the owner for 90 days. */
export function removePublicCv(
  auth: TokenSource,
): Promise<PublicCvResult<null>> {
  return call(async () => {
    const res = await apiFetch(auth, PATH, { method: 'DELETE' })
    if (!res.ok) return failure(res)
    return { kind: 'ok', value: null }
  })
}

/** Asks whether a handle can be taken. The handle is sent as typed; the caller checks its form first. */
export function checkHandle(
  auth: TokenSource,
  handle: string,
  signal?: AbortSignal,
): Promise<PublicCvResult<HandleCheck>> {
  return call(async () => {
    const res = await apiFetch(
      auth,
      `${PATH}/handle?handle=${encodeURIComponent(handle)}`,
      { signal: signal ?? null },
    )
    if (!res.ok) return failure(res)
    return { kind: 'ok', value: (await res.json()) as HandleCheck }
  })
}

/**
 * Asks for a preview of the settings on screen (saved or not). The answer is a path to open in a new tab; the rendered
 * CV is never read by this page.
 */
export function previewPublicCv(
  auth: TokenSource,
  settings: PublicCvSettings,
  signal?: AbortSignal,
): Promise<PublicCvResult<{ path: string }>> {
  return call(async () => {
    const res = await apiFetch(auth, PREVIEW_PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ settings }),
      signal: signal ?? null,
    })
    if (!res.ok) return failure(res)
    const body = (await res.json()) as { path?: unknown }
    // Only a preview path of the backend is ever opened.
    if (typeof body.path !== 'string' || !PREVIEW_ANSWER.test(body.path)) {
      return {
        kind: 'failed',
        code: null,
        message: messageForError(new Error('preview path')),
      }
    }
    return { kind: 'ok', value: { path: body.path } }
  })
}

async function call<T>(
  run: () => Promise<PublicCvResult<T>>,
): Promise<PublicCvResult<T>> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', code: null, message: messageForError(e) }
  }
}

async function failure<T>(res: Response): Promise<PublicCvResult<T>> {
  const code = await errorCodeOf(res)
  const known = knownCode(code) ?? (res.status === 429 ? 'rate_limited' : null)
  return {
    kind: 'failed',
    code: known,
    message: known ? MESSAGES[known] : messageForStatus(res.status),
  }
}

function knownCode(code: string | null): PublicCvErrorCode | null {
  return code !== null && code in MESSAGES ? (code as PublicCvErrorCode) : null
}

async function errorCodeOf(res: Response): Promise<string | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    return typeof body.error === 'string' ? body.error : null
  } catch {
    return null
  }
}
