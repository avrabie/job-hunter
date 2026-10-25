import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { User } from 'oidc-client-ts'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import {
  AuthContext,
  type AuthApi,
  type AuthState,
} from '../auth/authContext.ts'
import { fakeAuth } from '../test/fakeAuth.ts'
import { fakeUser } from '../test/fakeManager.ts'

function mount(auth: AuthApi, path = '/') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <App />
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
    const main = within(screen.getByRole('main'))
    expect(main.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(main.getByRole('link', { name: 'Register' })).toHaveAttribute(
      'href',
      '/register',
    )
    await userEvent.click(main.getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledTimes(1)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('has the minimal header with Sign in and Register and no main navigation', () => {
    vi.stubGlobal('fetch', vi.fn())
    mount(fakeAuth({ status: 'signedOut' }))
    const header = within(screen.getByRole('banner'))
    expect(header.getByRole('link', { name: 'Job Hunter' })).toHaveAttribute(
      'href',
      '/',
    )
    expect(header.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(header.getByRole('link', { name: 'Register' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Main' })).toBeNull()
    expect(screen.queryByText(/Service status/)).toBeNull()
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
    expect(
      within(screen.getByRole('banner')).getByText('ada@example.ch'),
    ).toBeInTheDocument()
    expect(
      await within(screen.getByRole('contentinfo')).findByText(
        'The backend has no endpoints yet.',
      ),
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
    const nav = within(screen.getByRole('navigation', { name: 'Main' }))
    expect(nav.getByRole('link', { name: 'Job tracking' })).toHaveAttribute(
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

  it('shows an overview with cards for Matches, Job tracking and Profile', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('x', { status: 404 })),
    )
    mount(fakeAuth(signedIn()))
    const main = within(screen.getByRole('main'))
    expect(main.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(
      within(main.getByRole('region', { name: 'Matches' })).getByRole('link'),
    ).toHaveAttribute('href', '/matches')
    expect(
      within(main.getByRole('region', { name: 'Job tracking' })).getByRole(
        'link',
      ),
    ).toHaveAttribute('href', '/applications')
    expect(
      within(main.getByRole('region', { name: 'Profile' })).getByRole('link'),
    ).toHaveAttribute('href', '/profile')
    await screen.findByText('The backend has no endpoints yet.')
  })
})

describe('landmarks', () => {
  it.each([
    ['signed in', { status: 'signedIn', user: fakeUser() } as AuthState],
    ['signed out', { status: 'signedOut' } as AuthState],
  ])(
    'has one header, main and footer and one h1 inside main when %s',
    async (_, state) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => new Response('x', { status: 404 })),
      )
      mount(fakeAuth(state))
      expect(screen.getAllByRole('banner')).toHaveLength(1)
      expect(screen.getAllByRole('main')).toHaveLength(1)
      expect(screen.getAllByRole('contentinfo')).toHaveLength(1)
      expect(screen.getAllByRole('navigation').length).toBeGreaterThan(0)
      expect(
        within(screen.getByRole('main')).getAllByRole('heading', { level: 1 }),
      ).toHaveLength(1)
      expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
      if (state.status === 'signedIn') {
        await screen.findByText('The backend has no endpoints yet.')
      }
    },
  )

  it.each([
    '/postings',
    '/matches',
    '/applications',
    '/profile',
    '/export',
    '/publish',
    '/admin',
  ])(
    'shows the landing page in the one main for a signed-out visit to %s',
    (path) => {
      vi.stubGlobal('fetch', vi.fn())
      mount(fakeAuth({ status: 'signedOut' }), path)
      expect(screen.getAllByRole('main')).toHaveLength(1)
      expect(
        within(screen.getByRole('main')).getAllByRole('heading', { level: 1 }),
      ).toHaveLength(1)
    },
  )
})

describe('other states', () => {
  it('shows a retry when the sign-in service cannot be reached', async () => {
    const auth = fakeAuth({ status: 'error' })
    mount(auth)
    await userEvent.click(
      within(screen.getByRole('main')).getByRole('button', {
        name: 'Try again',
      }),
    )
    expect(auth.signIn).toHaveBeenCalledTimes(1)
  })

  it('shows a loading text while the state is decided, without the sign-in actions', () => {
    mount(fakeAuth({ status: 'loading' }))
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign in' })).toBeNull()
  })
})
