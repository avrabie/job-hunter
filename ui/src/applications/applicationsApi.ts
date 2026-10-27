import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

export const STAGES = ['INTERESTED', 'APPLYING', 'APPLIED', 'REJECTED'] as const

export type ApplicationStage = (typeof STAGES)[number]

export const STAGE_LABEL: Record<ApplicationStage, string> = {
  INTERESTED: 'Interested',
  APPLYING: 'Applying',
  APPLIED: 'Applied',
  REJECTED: 'Rejected',
}

/** What the live posting looks like now: still listed, changed since the user marked it, or no longer listed. */
export type LiveStatus = 'OPEN' | 'CHANGED' | 'GONE'

export interface ApplicationSummary {
  id: string
  postingId: string | null
  title: string
  company: string
  stage: ApplicationStage
  appliedOn: string | null
  stageChangedAt: string
  liveStatus: LiveStatus
}

/** The frozen copy of the posting as the user saw it, plus notes. */
export interface ApplicationDetail extends ApplicationSummary {
  url: string
  description: string
  capturedAt: string
  postingLastSeen: string | null
  notes: string
  letterCount: number
}

/** The fields the user can change; `appliedOn: null` clears the date. */
export interface ApplicationPatch {
  stage?: ApplicationStage
  notes?: string
  appliedOn?: string | null
}

/** The application that a posting already has for this user. */
export interface PostingStage {
  postingId: string
  applicationId: string
  stage: ApplicationStage
}

export type Unauthorized = { kind: 'unauthorized' }
export type Failed = { kind: 'failed'; message: string }

export type ListResult =
  { kind: 'loaded'; applications: ApplicationSummary[] } | Unauthorized | Failed

export type DetailResult =
  | { kind: 'loaded'; application: ApplicationDetail }
  | { kind: 'notFound' }
  | Unauthorized
  | Failed

export type ActionResult = { kind: 'done' } | Unauthorized | Failed

export type TrackResult =
  { kind: 'tracked'; application: ApplicationSummary } | Unauthorized | Failed

/** The fields of the Swiss RAV job-search proof. A null field is one the application does not have. */
export interface RavProof {
  companyAddress: string | null
  position: string
  contactPerson: string | null
  contactPhone: string | null
  contactEmail: string | null
  postalCode: string | null
  postingUrl: string
}

export type RavResult =
  | { kind: 'loaded'; proof: RavProof }
  | { kind: 'notApplied' }
  | { kind: 'notFound' }
  | Unauthorized
  | Failed

export type StagesResult =
  { kind: 'loaded'; stages: PostingStage[] } | Unauthorized | Failed

const PATH = '/api/applications'

/** Fixed texts for the error codes the backend sends as `{"error": code}`. The body itself is never shown. */
const CODE_MESSAGES: Record<string, string> = {
  application_limit:
    'You can keep at most 500 applications. Remove one to track another.',
}

const NOT_FOUND = 'That application no longer exists.'

/** The error code of a failed response, or null. Only the code is read; the rest of the body is dropped. */
async function errorCode(res: Response): Promise<string | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    return typeof body.error === 'string' ? body.error : null
  } catch {
    return null
  }
}

async function failure(
  res: Response,
  texts: Record<number, string> = {},
): Promise<Failed> {
  const code = await errorCode(res)
  const message =
    (code !== null ? CODE_MESSAGES[code] : undefined) ??
    texts[res.status] ??
    messageForStatus(res.status)
  return { kind: 'failed', message }
}

async function guarded<T extends { kind: string }>(
  run: () => Promise<T>,
): Promise<T | Unauthorized | Failed> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

function isStage(value: unknown): value is ApplicationStage {
  return (
    typeof value === 'string' && (STAGES as readonly string[]).includes(value)
  )
}

export function listApplications(auth: TokenSource): Promise<ListResult> {
  return guarded<ListResult>(async () => {
    const res = await apiFetch(auth, PATH)
    if (!res.ok) return failure(res)
    return {
      kind: 'loaded',
      applications: (await res.json()) as ApplicationSummary[],
    }
  })
}

export function loadApplication(
  auth: TokenSource,
  id: string,
): Promise<DetailResult> {
  return guarded<DetailResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/${encodeURIComponent(id)}`)
    if (res.status === 404) return { kind: 'notFound' }
    if (!res.ok) return failure(res)
    return {
      kind: 'loaded',
      application: (await res.json()) as ApplicationDetail,
    }
  })
}

/** The RAV proof fields of an application that is in stage APPLIED. */
export function loadRavProof(
  auth: TokenSource,
  id: string,
): Promise<RavResult> {
  return guarded<RavResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/${encodeURIComponent(id)}/rav`)
    if (res.status === 404) return { kind: 'notFound' }
    if (res.status === 409) return { kind: 'notApplied' }
    if (!res.ok) return failure(res)
    return { kind: 'loaded', proof: (await res.json()) as RavProof }
  })
}

/** Starts tracking a posting. The answer is the existing application when the posting is already tracked. */
export function trackPosting(
  auth: TokenSource,
  postingId: string,
): Promise<TrackResult> {
  return guarded<TrackResult>(async () => {
    const res = await apiFetch(auth, PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ postingId }),
    })
    if (!res.ok) return failure(res, { 404: 'That posting no longer exists.' })
    return {
      kind: 'tracked',
      application: (await res.json()) as ApplicationSummary,
    }
  })
}

export function updateApplication(
  auth: TokenSource,
  id: string,
  patch: ApplicationPatch,
): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    })
    if (!res.ok)
      return failure(res, {
        400: 'The stage, notes or date were not accepted.',
        404: NOT_FOUND,
      })
    return { kind: 'done' }
  })
}

export function removeApplication(
  auth: TokenSource,
  id: string,
): Promise<ActionResult> {
  return guarded<ActionResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
    if (!res.ok) return failure(res, { 404: NOT_FOUND })
    return { kind: 'done' }
  })
}

/** The stages of the user's applications for the given postings. Entries with an unknown shape are left out. */
export function loadPostingStages(
  auth: TokenSource,
  postingIds: string[],
): Promise<StagesResult> {
  return guarded<StagesResult>(async () => {
    const params = new URLSearchParams({ ids: postingIds.join(',') })
    const res = await apiFetch(auth, `${PATH}/by-posting?${params.toString()}`)
    if (!res.ok) return failure(res)
    const body = (await res.json()) as unknown
    const stages = Array.isArray(body)
      ? body.flatMap((item): PostingStage[] => {
          const entry = item as Partial<PostingStage>
          return typeof entry.postingId === 'string' &&
            typeof entry.applicationId === 'string' &&
            isStage(entry.stage)
            ? [
                {
                  postingId: entry.postingId,
                  applicationId: entry.applicationId,
                  stage: entry.stage,
                },
              ]
            : []
        })
      : []
    return { kind: 'loaded', stages }
  })
}
