import { vi } from 'vitest'
import type { AuthApi, AuthState } from '../auth/authContext.ts'
import { fakeUser } from './fakeManager.ts'

export const signedIn = (): AuthState => ({
  status: 'signedIn',
  user: fakeUser(),
})

/** An [AuthApi] with spies, for pages that only read the sign-in state and call the API client. */
export function fakeAuth(
  state: AuthState,
  over: Partial<AuthApi> = {},
): AuthApi {
  return {
    state,
    signIn: vi.fn(async () => {}),
    signOut: vi.fn(async () => {}),
    completeSignIn: vi.fn(async () => ({ signedIn: true, returnTo: '/' })),
    getAccessToken: () =>
      state.status === 'signedIn' ? state.user.access_token : null,
    renew: vi.fn(async () => null),
    discard: vi.fn(async () => {}),
    ...over,
  }
}
