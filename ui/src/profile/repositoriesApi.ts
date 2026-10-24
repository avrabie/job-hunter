import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import type { CodeProvider, Repository } from './types.ts'

export type RepositoriesResult =
  | { kind: 'found'; repositories: Repository[] }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/profile/repositories'

// Fixed texts for the error codes of the backend; the body is never shown.
const LISTING_MESSAGES: Record<string, string> = {
  invalid_username: 'That username is not valid for this provider.',
  account_not_found: 'No account with that username was found.',
  provider_rate_limited:
    'The provider is limiting requests right now. Please try again later.',
  provider_unavailable:
    'The provider could not be reached right now. Please try again.',
}

/** Lists the public repositories of one account. Nothing is saved by this call. */
export async function listRepositories(
  auth: TokenSource,
  provider: CodeProvider,
  username: string,
): Promise<RepositoriesResult> {
  try {
    const query = new URLSearchParams({ provider, username })
    const res = await apiFetch(auth, `${PATH}?${query}`)
    if (!res.ok) {
      return { kind: 'failed', message: await failureMessage(res) }
    }
    const body = (await res.json()) as { repositories?: Repository[] }
    return { kind: 'found', repositories: body.repositories ?? [] }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

async function failureMessage(res: Response): Promise<string> {
  const code = await errorCodeOf(res)
  if (code !== null && code in LISTING_MESSAGES) return LISTING_MESSAGES[code]!
  if (res.status === 429) return LISTING_MESSAGES.provider_rate_limited!
  return messageForStatus(res.status)
}

async function errorCodeOf(res: Response): Promise<string | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    return typeof body.error === 'string' ? body.error : null
  } catch {
    return null
  }
}
