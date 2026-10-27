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
    expect(fetchSpy.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/public/postings?size=6',
    ])
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
    // The overview also asks for the tracked jobs; the status check itself runs once.
    expect(fetchSpy.mock.calls.filter(([url]) => url === '/api/')).toHaveLength(
      1,
    )
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

describe('signed-in overview', () => {
  const app = (stage: string, id: string) => ({
    id,
    postingId: null,
    title: 'Backend Engineer',
    company: 'Example AG',
    stage,
    appliedOn: null,
    stageChangedAt: '2026-01-01T00:00:00Z',
    liveStatus: 'OPEN',
  })

  /** `/api/` answers 200; the list answers with `list`, or the given status when it is a number. */
  function stubApi(list: unknown[] | number) {
    const fetchSpy = vi.fn(async (url: string) => {
      if (url !== '/api/applications') return new Response('x', { status: 200 })
      return typeof list === 'number'
        ? new Response('x', { status: list })
        : new Response(JSON.stringify(list), { status: 200 })
    })
    vi.stubGlobal('fetch', fetchSpy)
    return fetchSpy
  }

  const signedInAuth = () =>
    fakeAuth({ status: 'signedIn', user: fakeUser() } as AuthState)

  const jobTracking = () =>
    within(screen.getByRole('main')).getByRole('region', {
      name: 'Job tracking',
    })

  it('greets the user by name and links to the main areas', async () => {
    stubApi([])
    mount(signedInAuth())
    const main = within(screen.getByRole('main'))
    expect(
      main.getByRole('heading', { level: 1, name: 'Welcome, Ada Lovelace' }),
    ).toBeInTheDocument()
    const quick = within(main.getByRole('navigation', { name: 'Quick links' }))
    expect(quick.getByRole('link', { name: 'Postings' })).toHaveAttribute(
      'href',
      '/postings',
    )
    expect(quick.getByRole('link', { name: 'Export CV' })).toHaveAttribute(
      'href',
      '/export',
    )
    expect(quick.getByRole('link', { name: 'Public CV' })).toHaveAttribute(
      'href',
      '/publish',
    )
    await screen.findByText(/not tracking any jobs yet/)
  })

  it('greets by email when the token has no name', async () => {
    stubApi([])
    const user = {
      ...fakeUser(),
      profile: { sub: 'u', email: 'ada@example.ch' },
    } as unknown as User
    mount(fakeAuth({ status: 'signedIn', user }))
    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Welcome, ada@example.ch',
      }),
    ).toBeInTheDocument()
    await screen.findByText(/not tracking any jobs yet/)
  })

  it('counts the tracked jobs per stage from the list', async () => {
    const fetchSpy = stubApi([
      app('INTERESTED', 'a'),
      app('INTERESTED', 'b'),
      app('APPLYING', 'c'),
      app('APPLIED', 'd'),
      app('INTERESTED', 'e'),
    ])
    mount(signedInAuth())
    const counts = await within(jobTracking()).findByRole('list', {
      name: 'Tracked jobs per stage',
    })
    expect(
      within(counts)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['3 Interested', '1 Applying', '1 Applied', '0 Rejected'])
    expect(
      fetchSpy.mock.calls.filter(([url]) => url === '/api/applications'),
    ).toHaveLength(1)
  })

  it('says so when no job is tracked yet', async () => {
    stubApi([])
    mount(signedInAuth())
    expect(
      await within(jobTracking()).findByText(/not tracking any jobs yet/),
    ).toBeInTheDocument()
    expect(
      within(jobTracking()).getByRole('link', { name: 'Open job tracking' }),
    ).toHaveAttribute('href', '/applications')
  })

  it('keeps the page working when the count fails', async () => {
    stubApi(500)
    mount(signedInAuth())
    expect(
      await within(jobTracking()).findByText(
        /Your tracked jobs could not be counted\./,
      ),
    ).toBeInTheDocument()
    expect(
      within(screen.getByRole('main')).getByRole('region', { name: 'Matches' }),
    ).toBeInTheDocument()
    expect(within(jobTracking()).queryByRole('list')).toBeNull()
  })

  it('shows a counting text while the list loads', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>(() => {})),
    )
    mount(signedInAuth())
    expect(
      within(jobTracking()).getByText('Counting your tracked jobs…'),
    ).toBeInTheDocument()
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
