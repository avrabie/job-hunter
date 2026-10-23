import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { MATCH_PAGE_SIZE, type MatchItem } from './types.ts'

export type MatchesResult =
  | { kind: 'loaded'; matches: MatchItem[] }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export function matchesPath(page: number): string {
  return `/api/matches?page=${page}&size=${MATCH_PAGE_SIZE}`
}

/** Results carry short fixed texts, never a response body. */
export async function loadMatches(
  auth: TokenSource,
  page: number,
): Promise<MatchesResult> {
  try {
    const res = await apiFetch(auth, matchesPath(page))
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', matches: (await res.json()) as MatchItem[] }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}
