import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

export interface Schedule {
  enabled: boolean
  cron: string
  zone: string
  nextFire?: string | null
  updatedAt?: string | null
}

export type ScheduleResult =
  | { kind: 'loaded'; schedule: Schedule }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden' }
  | { kind: 'invalid'; field: string; message: string }
  | { kind: 'failed'; message: string }

const PATH = '/api/admin/crawl-schedule'

/** Results carry short fixed texts or the server's validation text, never a response body as such. */
export async function loadSchedule(auth: TokenSource): Promise<ScheduleResult> {
  return call(auth)
}

export async function saveSchedule(
  auth: TokenSource,
  change: { enabled: boolean; cron: string; zone: string },
): Promise<ScheduleResult> {
  return call(auth, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      enabled: change.enabled,
      cron: change.cron,
      zone: change.zone,
    }),
  })
}

async function call(
  auth: TokenSource,
  init?: RequestInit,
): Promise<ScheduleResult> {
  try {
    const res = await apiFetch(auth, PATH, init)
    if (res.status === 403) return { kind: 'forbidden' }
    if (res.status === 400) return invalid(res)
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', schedule: (await res.json()) as Schedule }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

/** The 400 body is `{error, field, message}` with fixed server texts; anything else becomes a generic message. */
async function invalid(res: Response): Promise<ScheduleResult> {
  try {
    const body = (await res.json()) as { field?: unknown; message?: unknown }
    if (typeof body.field === 'string' && typeof body.message === 'string')
      return { kind: 'invalid', field: body.field, message: body.message }
  } catch {
    // fall through to the generic text
  }
  return { kind: 'invalid', field: '', message: 'The schedule was rejected.' }
}
