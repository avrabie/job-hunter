import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { emptyProfile, type Profile } from '../profile/types.ts'

export type DraftResult =
  | { kind: 'draft'; profile: Profile }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/cv'

const EXTRACT_MESSAGES: Record<number, string> = {
  404: 'Upload a CV first.',
  409: 'Choose a master CV in your files first.',
  413: 'The CV text is too long to read.',
  422: 'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
  429: 'Too many extractions. Please try again later.',
  502: 'The CV could not be read right now. Please try again.',
  503: 'Reading CVs is not set up on this server yet.',
}

async function guarded<T extends { kind: string }>(
  run: () => Promise<T>,
): Promise<T | { kind: 'unauthorized' } | { kind: 'failed'; message: string }> {
  try {
    return await run()
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

/** Asks the backend to read the stored CV. The draft is not saved anywhere by this call. */
export function extractProfile(auth: TokenSource): Promise<DraftResult> {
  return guarded<DraftResult>(async () => {
    const res = await apiFetch(auth, `${PATH}/extract`, { method: 'POST' })
    if (!res.ok)
      return {
        kind: 'failed',
        message: EXTRACT_MESSAGES[res.status] ?? messageForStatus(res.status),
      }
    return { kind: 'draft', profile: withDefaults(await res.json()) }
  })
}

/** The draft with every field present, so a missing one cannot break the form. */
function withDefaults(draft: Partial<Profile>): Profile {
  const base = emptyProfile()
  return {
    ...base,
    ...draft,
    preferences: { ...base.preferences, ...draft.preferences },
  }
}

/**
 * The CV fills the facts it can know. The user's preferences (roles, workload, salary...) are not in a CV and stay as
 * they are.
 */
export function applyDraft(current: Profile, draft: Profile): Profile {
  return { ...draft, preferences: current.preferences }
}
