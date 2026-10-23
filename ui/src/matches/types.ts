export const MATCH_PAGE_SIZE = 20

/** One passing match, as `GET /api/matches` returns it. */
export interface MatchItem {
  postingId: string
  title: string
  company: string
  city: string | null
  canton: string | null
  url: string
  score: number
  reasons: string[]
}
