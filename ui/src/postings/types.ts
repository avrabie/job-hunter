/** The original ad of one source; the primary source comes first. */
export interface PostingLink {
  sourceId: string
  url: string
  primary: boolean
}

export interface PostingSummary {
  id: string
  title: string
  company: string
  city: string | null
  canton: string | null
  language: string
  firstSeen: string
  lastSeen: string
  links: PostingLink[]
}

export interface PostingDetail extends PostingSummary {
  description: string
}

export interface ListQuery {
  q: string
  canton: string
  source: string
  page: number
}

export const PAGE_SIZE = 20
