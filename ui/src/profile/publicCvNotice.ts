import { apiFetch, type TokenSource } from '../api/apiFetch.ts'

/**
 * Whether the public CV is live, as the profile page shows it. `unknown` when the state could not be read; the page
 * then shows nothing about it.
 */
export type PublicCvState =
  | { state: 'live'; handle: string }
  | { state: 'not-live' }
  | { state: 'unknown' }

const PATH = '/api/cv/public'

/** Reads the public CV state from `GET /api/cv/public`. Never throws: any failure is `unknown`. */
export async function loadPublicCvState(
  auth: TokenSource,
): Promise<PublicCvState> {
  try {
    const res = await apiFetch(auth, PATH)
    if (!res.ok) return { state: 'unknown' }
    const body = (await res.json()) as {
      configured?: unknown
      published?: unknown
      handle?: unknown
    }
    if (body.configured === true && body.published === true) {
      return typeof body.handle === 'string'
        ? { state: 'live', handle: body.handle }
        : { state: 'unknown' }
    }
    if (body.configured === false) return { state: 'not-live' }
    if (body.configured === true && body.published === false)
      return { state: 'not-live' }
    return { state: 'unknown' }
  } catch {
    return { state: 'unknown' }
  }
}

/** The address of the public page, on the origin the user is using. */
export function publicCvAddress(handle: string): string {
  return `${window.location.origin}/cv/${handle}`
}
