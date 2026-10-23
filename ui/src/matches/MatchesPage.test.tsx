import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { MatchesPage } from './MatchesPage.tsx'
import { matchesPath } from './matchesApi.ts'
import type { MatchItem } from './types.ts'

const match = (over: Partial<MatchItem> = {}): MatchItem => ({
  postingId: '11111111-1111-1111-1111-111111111111',
  title: 'Backend Engineer',
  company: 'Acme',
  city: 'Zurich',
  canton: 'ZH',
  url: 'https://example.org/jobs/1',
  score: 82,
  reasons: ['CANTON_MATCH', 'SKILL_MATCH'],
  ...over,
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

function stubFetch(handler: (url: string) => Response) {
  const urls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      urls.push(url)
      return handler(url)
    }),
  )
  return urls
}

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/matches']}>
        <Routes>
          <Route path="/matches" element={<MatchesPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

beforeEach(() => {
  ;(['log', 'info', 'warn', 'error', 'debug'] as const).forEach((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('matchesPath', () => {
  it('carries page and size', () => {
    expect(matchesPath(2)).toBe('/api/matches?page=2&size=20')
  })
})

describe('the matches page', () => {
  it('shows title, company, city, score, reasons and a safe link', async () => {
    stubFetch(() => json(200, [match()]))
    mount()

    expect(
      await screen.findByRole('heading', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Acme · Zurich \(ZH\) · score 82/),
    ).toBeInTheDocument()
    expect(screen.getByText('SKILL_MATCH')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'Open original' })
    expect(link).toHaveAttribute('href', 'https://example.org/jobs/1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
    expect(link.getAttribute('rel')).toContain('noreferrer')
  })

  it('renders no anchor for an unsafe url', async () => {
    stubFetch(() => json(200, [match({ url: 'javascript:alert(1)' })]))
    mount()

    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(
      screen.queryByRole('link', { name: 'Open original' }),
    ).not.toBeInTheDocument()
  })

  it('shows the save-your-profile hint when empty', async () => {
    stubFetch(() => json(200, []))
    mount()

    expect(
      await screen.findByRole('link', { name: 'Save your profile first' }),
    ).toHaveAttribute('href', '/profile')
  })

  it('shows a fixed message and a working retry on a server error', async () => {
    let fail = true
    stubFetch(() =>
      fail ? json(500, { secret: 'BODY' }) : json(200, [match()]),
    )
    mount()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/BODY/)).not.toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(
      await screen.findByRole('heading', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
  })

  it('shows the landing page and requests nothing when signed out', () => {
    const urls = stubFetch(() => json(200, []))
    mount(fakeAuth({ status: 'signedOut' }))

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(urls).toEqual([])
  })

  it('pages forward when the page is full', async () => {
    const full = Array.from({ length: 20 }, (_, i) =>
      match({ postingId: `id-${i}`, title: `Job ${i}` }),
    )
    const urls = stubFetch((url) =>
      url.includes('page=1')
        ? json(200, [match({ title: 'Last' })])
        : json(200, full),
    )
    mount()
    await screen.findByRole('heading', { name: 'Job 0' })

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(
      await screen.findByRole('heading', { name: 'Last' }),
    ).toBeInTheDocument()
    expect(urls.at(-1)).toContain('page=1')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })
})
