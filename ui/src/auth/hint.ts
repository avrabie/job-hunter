/**
 * One boolean flag: "someone signed in in this browser". It holds no token and no identity. After a reload it tells
 * the UI that a session at the auth service is worth looking for. Storage can be blocked, so every access is guarded.
 */
const KEY = 'jh.signedIn'

export function hasSignedInHint(): boolean {
  try {
    return window.localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function setSignedInHint(): void {
  try {
    window.localStorage.setItem(KEY, '1')
  } catch {
    // Without the flag a reload shows the landing page; nothing else depends on it.
  }
}

export function clearSignedInHint(): void {
  try {
    window.localStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}
