import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { toRequestProfile, type Profile, type StoredProfile } from './types.ts'

export type LoadResult =
  | { kind: 'loaded'; stored: StoredProfile }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type SaveResult =
  | { kind: 'saved'; stored: StoredProfile }
  | { kind: 'invalid'; fields: Record<string, string> }
  | { kind: 'conflict' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/profile'

/** Reads the signed-in user's profile. Results carry short fixed texts, never a response body. */
export async function loadProfile(auth: TokenSource): Promise<LoadResult> {
  try {
    const res = await apiFetch(auth, PATH)
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', stored: (await res.json()) as StoredProfile }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

/** Replaces the profile. `version` is the one that was loaded; a stale one comes back as `conflict`. */
export async function saveProfile(
  auth: TokenSource,
  version: number,
  profile: Profile,
): Promise<SaveResult> {
  try {
    const res = await apiFetch(auth, PATH, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ version, profile: toRequestProfile(profile) }),
    })
    if (res.ok)
      return { kind: 'saved', stored: (await res.json()) as StoredProfile }
    if (res.status === 409) return { kind: 'conflict' }
    if (res.status === 400)
      return { kind: 'invalid', fields: await fieldsOf(res) }
    if (res.status === 413)
      return { kind: 'failed', message: 'The profile is too large to save.' }
    return { kind: 'failed', message: messageForStatus(res.status) }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

async function fieldsOf(res: Response): Promise<Record<string, string>> {
  try {
    const body = (await res.json()) as { fields?: Record<string, string> }
    return body.fields ?? {}
  } catch {
    return {}
  }
}
