import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

/** One user in the list: account, profile name and tracked jobs per stage. */
export interface AdminUser {
  sub: string
  email: string | null
  displayName: string | null
  createdAt: string
  fullName: string | null
  hasProfile: boolean
  saved: number
  applying: number
  applied: number
  rejected: number
  lastActivity: string | null
}

export interface AdminProfile {
  fullName: string | null
  headline: string | null
  phone: string | null
  website: string | null
  linkedin: string | null
  city: string | null
  canton: string | null
}

export type AdminStage = 'INTERESTED' | 'APPLYING' | 'APPLIED' | 'REJECTED'

/** A tracked job without its description or notes. */
export interface AdminApplication {
  id: string
  stage: AdminStage
  title: string
  company: string
  url: string
  source: string
  capturedAt: string
  stageChangedAt: string
  appliedOn: string | null
}

export interface AdminUserDetail {
  user: AdminUser
  profile: AdminProfile | null
  applications: AdminApplication[]
}

function isDetail(body: unknown): body is AdminUserDetail {
  if (typeof body !== 'object' || body === null) return false
  const b = body as Partial<AdminUserDetail>
  return (
    typeof b.user === 'object' &&
    b.user !== null &&
    Array.isArray(b.applications)
  )
}

export type UsersResult =
  | { kind: 'loaded'; users: AdminUser[] }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: string }

export type UserResult =
  | { kind: 'loaded'; detail: AdminUserDetail }
  | { kind: 'notFound' }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: string }

/** Lists every user, newest sign-up first. Results carry fixed texts, never a response body. */
export async function loadUsers(auth: TokenSource): Promise<UsersResult> {
  try {
    const res = await apiFetch(auth, '/api/admin/users?limit=500')
    if (res.status === 403) return { kind: 'forbidden' }
    if (!res.ok) return { kind: 'failed', message: messageForStatus(res.status) }
    const body: unknown = await res.json()
    if (!Array.isArray(body))
      return { kind: 'failed', message: 'The user list had an unexpected shape.' }
    return { kind: 'loaded', users: body as AdminUser[] }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

export async function loadUser(
  auth: TokenSource,
  sub: string,
): Promise<UserResult> {
  try {
    const res = await apiFetch(
      auth,
      `/api/admin/users/${encodeURIComponent(sub)}`,
    )
    if (res.status === 403) return { kind: 'forbidden' }
    if (res.status === 404) return { kind: 'notFound' }
    if (!res.ok) return { kind: 'failed', message: messageForStatus(res.status) }
    const body: unknown = await res.json()
    if (!isDetail(body))
      return { kind: 'failed', message: 'The user had an unexpected shape.' }
    return { kind: 'loaded', detail: body }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}
