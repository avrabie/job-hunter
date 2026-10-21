import { ErrorResponse, type User, type UserManager } from 'oidc-client-ts'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { AuthContext, type AuthApi, type AuthState } from './authContext.ts'
import { clearSignedInHint, hasSignedInHint, setSignedInHint } from './hint.ts'
import { safeReturnTo } from './returnTo.ts'

interface Props {
  manager: UserManager
  children: ReactNode
}

export function AuthProvider({ manager, children }: Props) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })
  const userRef = useRef<User | null>(null)
  const redirected = useRef(false)

  const setUser = useCallback((user: User | null) => {
    userRef.current = user
    setState(user ? { status: 'signedIn', user } : { status: 'signedOut' })
  }, [])

  const discard = useCallback(async () => {
    clearSignedInHint()
    setUser(null)
    await manager.removeUser()
  }, [manager, setUser])

  // Restore after a reload: tokens are gone, but the session at the auth service may still exist.
  useEffect(() => {
    let cancelled = false
    const onCallback = window.location.pathname === '/callback'
    void manager.getUser().then(async (user) => {
      if (cancelled) return
      if (user && !user.expired) return setUser(user)
      // The callback page finishes a sign-in itself; a second redirect would start another one.
      if (onCallback || !hasSignedInHint()) return setUser(null)
      if (redirected.current) return
      redirected.current = true
      try {
        // Ask quietly (no sign-in form) and come back to the page that was open before the reload.
        await manager.signinRedirect({
          prompt: 'none',
          state: { returnTo: safeReturnTo(window.location.pathname) },
        })
      } catch {
        clearSignedInHint()
        if (!cancelled) setState({ status: 'error' })
      }
    })
    return () => {
      cancelled = true
    }
  }, [manager, setUser])

  useEffect(() => {
    const onLoaded = (user: User) => setUser(user)
    // A renewal that fails means the refresh token is no longer accepted: treat the user as signed out.
    const onRenewError = () => void discard()
    manager.events.addUserLoaded(onLoaded)
    manager.events.addSilentRenewError(onRenewError)
    return () => {
      manager.events.removeUserLoaded(onLoaded)
      manager.events.removeSilentRenewError(onRenewError)
    }
  }, [manager, setUser, discard])

  const signIn = useCallback(
    (returnTo?: string) =>
      manager.signinRedirect({ state: { returnTo: safeReturnTo(returnTo) } }),
    [manager],
  )

  const signOut = useCallback(async () => {
    clearSignedInHint()
    await manager.signoutRedirect()
  }, [manager])

  const completeSignIn = useCallback(
    async (url: string) => {
      try {
        const user = await manager.signinCallback(url)
        if (!user) throw new Error('Sign-in returned no user')
        setSignedInHint()
        setUser(user)
        return {
          signedIn: true,
          returnTo: safeReturnTo(
            (user.state as { returnTo?: unknown } | undefined)?.returnTo,
          ),
        }
      } catch (e) {
        clearSignedInHint()
        // The quiet check found no session: that is "signed out", not a failure.
        if (e instanceof ErrorResponse && e.error === 'login_required') {
          setUser(null)
          return {
            signedIn: false,
            returnTo: safeReturnTo(
              (e.state as { returnTo?: unknown } | undefined)?.returnTo,
            ),
          }
        }
        throw e
      }
    },
    [manager, setUser],
  )

  const getAccessToken = useCallback(
    () => userRef.current?.access_token ?? null,
    [],
  )

  const renew = useCallback(async () => {
    try {
      const user = await manager.signinSilent()
      if (!user) return null
      setUser(user)
      return user.access_token
    } catch {
      return null
    }
  }, [manager, setUser])

  const api = useMemo<AuthApi>(
    () => ({
      state,
      signIn,
      signOut,
      completeSignIn,
      getAccessToken,
      renew,
      discard,
    }),
    [state, signIn, signOut, completeSignIn, getAccessToken, renew, discard],
  )

  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>
}
