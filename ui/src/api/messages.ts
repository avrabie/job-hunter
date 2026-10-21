import { ApiError } from './apiFetch.ts'

/** A short text for the user about a response that was not a success. Never includes the response body. */
export function messageForStatus(status: number): string {
  if (status === 404) return 'The backend has no endpoints yet.'
  if (status === 502 || status === 503 || status === 504) {
    return 'The service is unavailable right now. Please try again.'
  }
  return `The request failed (status ${status}).`
}

export function messageForError(error: unknown): string {
  if (error instanceof ApiError && error.kind === 'unavailable') {
    return 'The service is unavailable right now. Please try again.'
  }
  return 'Something went wrong. Please try again.'
}
