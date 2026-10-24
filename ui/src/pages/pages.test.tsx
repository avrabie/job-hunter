import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { User } from 'oidc-client-ts'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  AuthContext,
  type AuthApi,
  type AuthState,
} from '../auth/authContext.ts'
import { fakeUser } from '../test/fakeManager.ts'
import { Home } from './Home.tsx'

function fakeAuth(state: AuthState, over: Partial<AuthApi> = {}): AuthApi {
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

function mount(auth: AuthApi) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Home />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('landing page', () => {
  it('offers sign in and register and makes no call to the API', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute(
      'href',
      '/register',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledTimes(1)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})

describe('signed-in shell', () => {
  const signedIn = (user: User = fakeUser()): AuthState => ({
    status: 'signedIn',
    user,
  })

  it('shows the email and no token text, and the backend message for a 404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('x', { status: 404 })),
    )
    mount(fakeAuth(signedIn()))
    expect(screen.getByText('ada@example.ch')).toBeInTheDocument()
    expect(
      await screen.findByText('The backend has no endpoints yet.'),
    ).toBeInTheDocument()
    for (const secret of ['access-1', 'refresh-1', 'id-1']) {
      expect(document.body).not.toHaveTextContent(secret)
    }
  })

  it('links to the job tracking page', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('x', { status: 200 })),
    )
    mount(fakeAuth(signedIn()))
    expect(screen.getByRole('link', { name: 'Job tracking' })).toHaveAttribute(
      'href',
      '/applications',
    )
  })

  it('shows the name when the token has no email', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('x', { status: 404 })),
    )
    const user = {
      ...fakeUser(),
      profile: { sub: 'u', name: 'Ada Lovelace' },
    } as unknown as User
    mount(fakeAuth(signedIn(user)))
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    await screen.findByText('The backend has no endpoints yet.')
  })

  it('says the service is unavailable and lets the user check again', async () => {
    const fetchSpy = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('network'))
      .mockResolvedValue(new Response('x', { status: 200 }))
    vi.stubGlobal('fetch', fetchSpy)
    mount(fakeAuth(signedIn()))
    expect(await screen.findByText(/unavailable/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }))
    await waitFor(() =>
      expect(screen.getByText('The backend answered.')).toBeInTheDocument(),
    )
  })

  it('calls /api/ once with the token and signs out on request', async () => {
    const fetchSpy = vi.fn(
      async (_u: string, _i?: RequestInit) =>
        new Response('x', { status: 404 }),
    )
    vi.stubGlobal('fetch', fetchSpy)
    const auth = fakeAuth(signedIn())
    mount(auth)
    await screen.findByText('The backend has no endpoints yet.')
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/')
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(auth.signOut).toHaveBeenCalledTimes(1)
  })
})

describe('other states', () => {
  it('shows a retry when the sign-in service cannot be reached', async () => {
    const auth = fakeAuth({ status: 'error' })
    mount(auth)
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(auth.signIn).toHaveBeenCalledTimes(1)
  })

  it('shows a loading text while the state is decided', () => {
    mount(fakeAuth({ status: 'loading' }))
    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })
})
