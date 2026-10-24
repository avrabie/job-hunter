import type { TokenSource } from '../api/apiFetch.ts'
import { PUBLIC_CV_PATH } from '../publiccv/options.ts'
import {
  getPublicCv,
  type PublicCvState as PublicCvPageState,
} from '../publiccv/publicCvApi.ts'

/**
 * Whether the public CV is live, as the profile page shows it. `unknown` when the state could not be read; the page
 * then shows nothing about it.
 */
export type PublicCvState =
  | { state: 'live'; handle: string }
  | { state: 'not-live' }
  | { state: 'unknown' }

/** Reads the public CV state through the shared client. Never throws: any failure is `unknown`. */
export async function loadPublicCvState(
  auth: TokenSource,
): Promise<PublicCvState> {
  try {
    const result = await getPublicCv(auth)
    if (result.kind !== 'ok') return { state: 'unknown' }
    return noticeOf(result.value)
  } catch {
    return { state: 'unknown' }
  }
}

/** Maps the page state the backend sent; the body is checked here because the shared client does not check it. */
function noticeOf(value: PublicCvPageState | null): PublicCvState {
  if (value === null || typeof value !== 'object') return { state: 'unknown' }
  if (value.configured === false) return { state: 'not-live' }
  if (value.configured !== true) return { state: 'unknown' }
  if (value.published === false) return { state: 'not-live' }
  if (value.published !== true) return { state: 'unknown' }
  return typeof value.handle === 'string'
    ? { state: 'live', handle: value.handle }
    : { state: 'unknown' }
}

/** The address of the public page, on the origin the user is using. */
export function publicCvAddress(handle: string): string {
  return `${window.location.origin}${PUBLIC_CV_PATH}${handle}`
}
