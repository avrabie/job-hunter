import type { User } from 'oidc-client-ts'
import { createContext, useContext } from 'react'

export type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; user: User }
  | { status: 'error' }

export interface AuthApi {
  state: AuthState
  /**
   * Starts the sign-in at the auth service (full page redirect). `returnTo` is one of our own paths to open afterwards
   * (see `safeReturnTo`); the default is the home page.
   */
  signIn: (returnTo?: string) => Promise<void>
  /** Ends the session at the auth service and returns to the landing page. */
  signOut: () => Promise<void>
  /**
   * Completes a sign-in from the `/callback` address and resolves to the path to open next. `signedIn` is false when
   * the quiet check found no session at the auth service (`login_required`). Rejects when the response is not valid.
   */
  completeSignIn: (
    url: string,
  ) => Promise<{ signedIn: boolean; returnTo: string }>
  /** The current access token, or null when signed out. */
  getAccessToken: () => string | null
  /** Renews the tokens with the refresh token. Returns the new access token, or null when renewal failed. */
  renew: () => Promise<string | null>
  /** Drops all in-memory tokens and the hint without contacting the auth service. */
  discard: () => Promise<void>
}

export const AuthContext = createContext<AuthApi | null>(null)

export function useAuth(): AuthApi {
  const api = useContext(AuthContext)
  if (!api) throw new Error('useAuth needs an AuthProvider above it')
  return api
}
