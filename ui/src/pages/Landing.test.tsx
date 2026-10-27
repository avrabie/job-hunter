import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth } from '../test/fakeAuth.ts'
import { Landing } from './Landing.tsx'
import { CAPABILITIES, PRIVACY_POINTS, STEPS } from './landingContent.ts'

function mount(auth: AuthApi, ui = <Landing returnTo="/matches" />) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthContext.Provider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

function posting(i: number, over: Record<string, unknown> = {}) {
  return {
    id: `p${i}`,
    title: `Engineer ${i}`,
    company: `Company ${i}`,
    city: 'Zurich',
    canton: 'ZH',
    remoteMode: null,
    salaryMin: null,
    salaryMax: null,
    language: 'en',
    firstSeen: new Date(Date.now() - 3 * 86_400_000).toISOString(),
    links: [{ label: 'Source', url: 'https://example.com/job' }],
    ...over,
  }
}

function stubPublic(items: unknown[], status = 200) {
  const fetchSpy = vi.fn((..._args: unknown[]) =>
    Promise.resolve(
      new Response(
        JSON.stringify({
          total: items.length,
          page: 0,
          size: 6,
          postings: items,
        }),
        {
          status,
        },
      ),
    ),
  )
  vi.stubGlobal('fetch', fetchSpy)
  return fetchSpy
}

describe('Landing', () => {
  it('has a hero with one h1, a one-sentence lead, Sign in and Register', async () => {
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    const hero = screen.getByRole('region', {
      name: 'Find Swiss software jobs that fit you',
    })
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(
      within(hero).getByText(/^Job Hunter matches .*review\.$/),
    ).toBeInTheDocument()
    expect(
      within(hero).getByRole('link', { name: 'Register' }),
    ).toHaveAttribute('href', '/register')
    await userEvent.click(within(hero).getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledWith('/matches')
  })

  it('shows six capability cards, from job matching to importing a job', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const section = within(
      screen.getByRole('region', { name: 'What you can do' }),
    )
    const cards = section.getAllByRole('listitem')
    expect(cards).toHaveLength(6)
    expect(
      section.getAllByRole('heading', { level: 3 }).map((h) => h.textContent),
    ).toEqual([
      'Find and match Swiss software jobs',
      'Job tracking',
      'Motivation letters',
      'CV export',
      'Public CV page',
      'Import a job from a URL',
    ])
    expect(cards.map((c) => c.textContent)).toEqual(
      CAPABILITIES.map((c) => `${c.title}${c.text}`),
    )
  })

  it('explains the three steps in order', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const steps = within(
      screen.getByRole('region', { name: 'How it works' }),
    ).getAllByRole('listitem')
    expect(steps).toHaveLength(3)
    expect(steps.map((s) => s.querySelector('h3')?.textContent)).toEqual([
      'Build your profile',
      'Find and track jobs',
      'Draft and export',
    ])
    expect(STEPS[2].text).toMatch(/You review everything/)
  })

  it('says the user reviews everything and nothing is sent to employers', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const privacy = within(
      screen.getByRole('region', { name: 'You stay in control' }),
    )
    expect(privacy.getAllByRole('listitem')).toHaveLength(PRIVACY_POINTS.length)
    expect(
      privacy.getByText(
        'You review everything. Nothing is ever sent to employers automatically.',
      ),
    ).toBeInTheDocument()
    expect(
      privacy.getByText('Your public CV is visible only if you publish it.'),
    ).toBeInTheDocument()
  })

  it('ends with a call to action that signs in or registers', async () => {
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    const cta = within(screen.getByRole('region', { name: 'Ready to start?' }))
    expect(
      cta.getByRole('link', { name: 'Create an account' }),
    ).toHaveAttribute('href', '/register')
    await userEvent.click(
      cta.getByRole('button', { name: 'Sign in to get started' }),
    )
    expect(auth.signIn).toHaveBeenCalledWith('/matches')
  })

  it('is what a signed-out visitor sees at / and makes no /api call but the public list', async () => {
    const fetchSpy = stubPublic([])
    const auth = fakeAuth({ status: 'signedOut' })
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    const main = within(screen.getByRole('main'))
    expect(
      main.getByRole('heading', {
        level: 1,
        name: 'Find Swiss software jobs that fit you',
      }),
    ).toBeInTheDocument()
    expect(main.getByRole('region', { name: 'How it works' })).toBeVisible()
    await userEvent.click(
      main.getByRole('button', { name: 'Sign in to get started' }),
    )
    expect(auth.signIn).toHaveBeenCalledWith(undefined)
    await waitFor(() => expect(fetchSpy).toHaveBeenCalled())
    expect(fetchSpy.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/public/postings?size=6',
    ])
  })
})

describe('Landing latest postings', () => {
  it('lists 6 postings with company, city, age and a sign-in action, and sends no token', async () => {
    const fetchSpy = stubPublic(
      Array.from({ length: 6 }, (_, i) =>
        posting(i, { description: 'SECRET TEXT' }),
      ),
    )
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    const section = within(
      await screen.findByRole('region', { name: 'Latest postings' }),
    )
    expect(section.getAllByRole('listitem')).toHaveLength(6)
    expect(
      section.getByText('Company 2 · Zurich · 3 days ago'),
    ).toBeInTheDocument()
    expect(section.queryByText(/SECRET TEXT/)).not.toBeInTheDocument()
    const buttons = section.getAllByRole('button', {
      name: /Sign in to read more/,
    })
    expect(buttons).toHaveLength(6)
    await userEvent.click(buttons[0])
    expect(auth.signIn).toHaveBeenCalledWith('/matches')
    const init = (fetchSpy.mock.calls[0] as unknown[])[1] as RequestInit
    expect(JSON.stringify(init.headers ?? {}).toLowerCase()).not.toContain(
      'authorization',
    )
    expect(init.credentials).toBe('omit')
    expect(auth.signIn).toHaveBeenCalledTimes(1)
  })

  it.each([
    [
      'a failing request',
      () => vi.fn((..._args: unknown[]) => Promise.reject(new TypeError('x'))),
    ],
    [
      'a 429',
      () =>
        vi.fn((..._args: unknown[]) =>
          Promise.resolve(new Response('', { status: 429 })),
        ),
    ],
  ])('is absent without an error on %s', async (_n, make) => {
    const fetchSpy = make()
    vi.stubGlobal('fetch', fetchSpy)
    mount(fakeAuth({ status: 'signedOut' }))
    await waitFor(() => expect(fetchSpy).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.queryByRole('region', { name: 'Latest postings' })).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByRole('region', { name: 'How it works' })).toBeVisible()
  })

  it('is absent when the list is empty', async () => {
    const fetchSpy = stubPublic([])
    mount(fakeAuth({ status: 'signedOut' }))
    await waitFor(() => expect(fetchSpy).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.queryByRole('region', { name: 'Latest postings' })).toBeNull()
  })

  it('shows markup in a title as literal text', async () => {
    stubPublic([posting(1, { title: '<b>x</b>' })])
    mount(fakeAuth({ status: 'signedOut' }))
    const section = await screen.findByRole('region', {
      name: 'Latest postings',
    })
    expect(within(section).getByText('<b>x</b>')).toBeInTheDocument()
    expect(section.querySelector('b')).toBeNull()
  })
})
