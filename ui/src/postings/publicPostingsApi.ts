/** One posting of the public list: metadata only, never the description. */
export interface PublicPosting {
  id: string
  title: string
  company: string
  city: string | null
  firstSeen: string
}

export const PUBLIC_POSTINGS_PATH = '/api/public/postings?size=6'

/**
 * The latest public postings. A plain `fetch`, not `apiFetch`: the endpoint needs no token, and a failure here
 * must never trigger a sign-in or a token renewal. Any failure (network, 429, other status, bad body) is an empty
 * list, so the caller simply shows nothing.
 */
export async function loadPublicPostings(
  signal?: AbortSignal,
): Promise<PublicPosting[]> {
  try {
    const res = await fetch(PUBLIC_POSTINGS_PATH, {
      credentials: 'omit',
      headers: { Accept: 'application/json' },
      signal,
    })
    if (!res.ok) return []
    const body = (await res.json()) as { postings?: unknown }
    if (!Array.isArray(body.postings)) return []
    return body.postings
      .filter(
        (p): p is PublicPosting =>
          typeof p === 'object' &&
          p !== null &&
          typeof p.id === 'string' &&
          typeof p.title === 'string' &&
          typeof p.company === 'string',
      )
      .slice(0, 6)
  } catch {
    return []
  }
}

/** "today", "1 day ago", "5 days ago", ... from an ISO timestamp; empty when the date is unusable. */
export function firstSeenAgo(
  firstSeen: string,
  now: Date = new Date(),
): string {
  const then = new Date(firstSeen).getTime()
  if (Number.isNaN(then)) return ''
  const days = Math.floor((now.getTime() - then) / 86_400_000)
  if (days < 1) return 'today'
  return days === 1 ? '1 day ago' : `${days} days ago`
}
