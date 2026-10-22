import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import {
  PAGE_SIZE,
  type ListQuery,
  type PostingDetail,
  type PostingSummary,
} from './types.ts'

export type ListResult =
  | { kind: 'loaded'; postings: PostingSummary[] }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

export type DetailResult =
  | { kind: 'loaded'; posting: PostingDetail }
  | { kind: 'notFound' }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

/** The request path for a list query; empty filters are left out. */
export function listPath(query: ListQuery): string {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(PAGE_SIZE),
  })
  if (query.q.trim()) params.set('q', query.q.trim())
  if (query.canton) params.set('canton', query.canton)
  if (query.source) params.set('source', query.source)
  return `/api/postings?${params.toString()}`
}

/** Results carry short fixed texts, never a response body. */
export async function loadPostings(
  auth: TokenSource,
  query: ListQuery,
): Promise<ListResult> {
  try {
    const res = await apiFetch(auth, listPath(query))
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', postings: (await res.json()) as PostingSummary[] }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

export async function loadPosting(
  auth: TokenSource,
  id: string,
): Promise<DetailResult> {
  try {
    const res = await apiFetch(auth, `/api/postings/${encodeURIComponent(id)}`)
    if (res.status === 404) return { kind: 'notFound' }
    if (!res.ok)
      return { kind: 'failed', message: messageForStatus(res.status) }
    return { kind: 'loaded', posting: (await res.json()) as PostingDetail }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}
