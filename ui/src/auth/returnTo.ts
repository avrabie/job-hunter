/** Pages the user may be returned to after signing in. A fixed list: no address from outside is ever followed. */
const RETURN_PATHS = ['/', '/profile']

/** The path to open after sign-in: [candidate] if it is one of ours, otherwise the home page. */
export function safeReturnTo(candidate: unknown): string {
  return typeof candidate === 'string' && RETURN_PATHS.includes(candidate)
    ? candidate
    : '/'
}
