/** What the API client needs from the sign-in module; `useAuth()` provides all three. */
export interface TokenSource {
  getAccessToken: () => string | null
  renew: () => Promise<string | null>
  discard: () => Promise<void>
}

export type ApiErrorKind = 'unauthorized' | 'unavailable'

/** Carries a kind only: never a response body, a token or a URL with query values. */
export class ApiError extends Error {
  readonly kind: ApiErrorKind

  constructor(kind: ApiErrorKind) {
    super(kind)
    this.kind = kind
  }
}

// Only the API of our own origin: a relative path under /api/, so the token cannot be sent anywhere else.
const API_PATH = /^\/api\/[^\\]*$/

export function isApiPath(path: string): boolean {
  return API_PATH.test(path) && !path.split(/[/?#]/).includes('..')
}

/**
 * Calls `/api/...` on the page's own origin with the user's access token. A 401 triggers one renewal and one repeat;
 * a second 401, or a failed renewal, discards the tokens (signed out) and throws `unauthorized`. A connection failure
 * throws `unavailable`. Any other status is returned for the caller to handle.
 */
export async function apiFetch(
  auth: TokenSource,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!isApiPath(path))
    throw new TypeError('apiFetch only calls paths under /api/')

  const token = auth.getAccessToken()
  if (!token) throw new ApiError('unauthorized')

  const first = await send(path, init, token)
  if (first.status !== 401) return first

  const renewed = await auth.renew()
  if (!renewed) return signedOut(auth)
  const second = await send(path, init, renewed)
  if (second.status === 401) return signedOut(auth)
  return second
}

async function signedOut(auth: TokenSource): Promise<never> {
  await auth.discard()
  throw new ApiError('unauthorized')
}

async function send(
  path: string,
  init: RequestInit,
  token: string,
): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  try {
    return await fetch(path, { ...init, headers, credentials: 'omit' })
  } catch {
    throw new ApiError('unavailable')
  }
}
