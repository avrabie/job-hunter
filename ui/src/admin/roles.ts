/**
 * Whether an access token lists a role in its `roles` claim. The signature is not checked: this only decides what to
 * show, and the backend enforces every role. Anything unreadable counts as "no".
 */
export function hasRole(token: string | null, role: string): boolean {
  if (!token) return false
  try {
    const payload = token.split('.')[1]
    if (!payload) return false
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(base64)) as { roles?: unknown }
    return Array.isArray(claims.roles) && claims.roles.includes(role)
  } catch {
    return false
  }
}
