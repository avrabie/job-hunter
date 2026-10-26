import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'

export type Importance = 'REQUIRED' | 'NICE_TO_HAVE'

export interface ProfileSkillRef {
  name: string
  profileIndex: number
}

export interface IncludedSkill extends ProfileSkillRef {
  importance: Importance
  profileName?: string
}

export interface EvidencedSkill {
  name: string
  importance: Importance
  kind: 'EXPERIENCE' | 'PROJECT'
  index: number
  source: string
  quote: string
}

export interface GapSkill {
  name: string
  importance: Importance
  related: ProfileSkillRef[]
  note: string | null
}

export type SkillAnalysis =
  | { analysed: false }
  | {
      analysed: true
      createdAt: string
      include: IncludedSkill[]
      /** Missing from an old backend: treated as empty. */
      evidenced?: EvidencedSkill[]
      gaps: GapSkill[]
      other: ProfileSkillRef[]
    }

export type SkillsErrorCode =
  'rate_limited' | 'skills_failed' | 'skills_not_configured' | 'notes_invalid'

/** Fixed texts per error code; the response body is never shown. */
export const SKILLS_MESSAGES: Record<SkillsErrorCode, string> = {
  rate_limited:
    'You have reached the limit of job analyses for this hour. Please try again later.',
  skills_failed: 'The job could not be analysed. Please try again.',
  skills_not_configured: 'The skill analysis is not available.',
  notes_invalid:
    'The notes are too long or contain HTML. Each note can have at most 500 characters.',
}

export type SkillsResult =
  | { kind: 'done'; analysis: SkillAnalysis }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; code?: SkillsErrorCode | null; message: string }

const base = (applicationId: string) =>
  `/api/applications/${encodeURIComponent(applicationId)}/skills`

async function errorCodeOf(res: Response): Promise<SkillsErrorCode | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    if (typeof body.error === 'string' && body.error in SKILLS_MESSAGES)
      return body.error as SkillsErrorCode
  } catch {
    // No readable body: the status decides.
  }
  if (res.status === 429) return 'rate_limited'
  if (res.status === 502) return 'skills_failed'
  if (res.status === 503) return 'skills_not_configured'
  return null
}

async function skillsResult(res: Response): Promise<SkillsResult> {
  if (res.ok)
    return { kind: 'done', analysis: (await res.json()) as SkillAnalysis }
  const code = await errorCodeOf(res)
  return {
    kind: 'failed',
    code,
    message: code ? SKILLS_MESSAGES[code] : messageForStatus(res.status),
  }
}

async function guarded(
  run: () => Promise<SkillsResult>,
): Promise<SkillsResult> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

/** The stored analysis, or `{analysed:false}`. No model call. */
export function readSkills(
  auth: TokenSource,
  applicationId: string,
): Promise<SkillsResult> {
  return guarded(async () =>
    skillsResult(await apiFetch(auth, base(applicationId))),
  )
}

/** Runs the analysis (a model call, counted against its own hourly limit) and replaces the stored one. */
export function analyseSkills(
  auth: TokenSource,
  applicationId: string,
): Promise<SkillsResult> {
  return guarded(async () =>
    skillsResult(
      await apiFetch(auth, `${base(applicationId)}/analyse`, {
        method: 'POST',
      }),
    ),
  )
}

/** Saves the notes of this application only; the profile is not touched. */
export function saveSkillNotes(
  auth: TokenSource,
  applicationId: string,
  notes: { skill: string; text: string }[],
): Promise<SkillsResult> {
  return guarded(async () =>
    skillsResult(
      await apiFetch(auth, `${base(applicationId)}/notes`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes }),
      }),
    ),
  )
}
