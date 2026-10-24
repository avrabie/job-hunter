import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import type { CodeProvider, ProjectAnalysis } from './types.ts'

export type AnalysisResult =
  | { kind: 'analyzed'; analysis: ProjectAnalysis }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; message: string }

const PATH = '/api/profile/open-source-projects/analyze'

// Fixed texts for the error codes of the backend; the body is never shown.
const ANALYSIS_MESSAGES: Record<string, string> = {
  project_not_found:
    'Save this project first, then analyze it. Only saved projects can be analyzed.',
  repository_not_found: 'This repository was not found on the provider.',
  too_many_analyses:
    'You have reached the limit of analyses for this hour. Please try again later.',
  provider_rate_limited:
    'The provider is limiting requests right now. Please try again later.',
  provider_unavailable:
    'The provider could not be reached right now. Please try again.',
  analysis_failed:
    'The analysis could not be completed. Please try again later.',
  analysis_not_configured: 'Analysis is not available on this server.',
}

/**
 * Analyses one saved repository. Nothing is saved by this call: the page keeps the analysis as an unsaved change
 * until the user presses Save.
 */
export async function analyzeProject(
  auth: TokenSource,
  provider: CodeProvider,
  fullName: string,
): Promise<AnalysisResult> {
  try {
    const res = await apiFetch(auth, PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider, fullName }),
    })
    if (!res.ok) {
      return { kind: 'failed', message: await failureMessage(res) }
    }
    const body = (await res.json()) as { analysis: ProjectAnalysis }
    return { kind: 'analyzed', analysis: body.analysis }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', message: messageForError(e) }
  }
}

async function failureMessage(res: Response): Promise<string> {
  const code = await errorCodeOf(res)
  if (code !== null && code in ANALYSIS_MESSAGES)
    return ANALYSIS_MESSAGES[code]!
  if (res.status === 429) return ANALYSIS_MESSAGES.provider_rate_limited!
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
