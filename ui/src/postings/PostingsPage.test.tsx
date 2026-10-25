import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { PostingPage } from './PostingPage.tsx'
import { PostingsPage } from './PostingsPage.tsx'
import type { PostingDetail, PostingSummary } from './types.ts'

const posting = (over: Partial<PostingSummary> = {}): PostingSummary => ({
  id: '11111111-1111-1111-1111-111111111111',
  title: 'Backend Engineer',
  company: 'Acme',
  city: 'Zurich',
  canton: 'ZH',
  language: 'en',
  firstSeen: '2026-10-22T10:00:00Z',
  lastSeen: '2026-10-23T10:00:00Z',
  links: [
    {
      sourceId: 'greenhouse-acme',
      url: 'https://example.org/jobs/1',
      primary: true,
    },
  ],
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

/** The posting list requests only; the stage lookup for the visible postings is asked after them. */
const listUrls = (urls: string[]) =>
  urls.filter((u) => !u.includes('by-posting'))

function mount(entry: string, auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/postings" element={<PostingsPage />} />
          <Route path="/postings/:id" element={<PostingPage />} />
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

describe('the list', () => {
  it('shows the postings with company, city and an original link', async () => {
    stubFetch(() => json(200, [posting()]))
    mount('/postings')

    expect(
      await screen.findByRole('link', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Acme · Zurich \(ZH\) · en · first seen 2026-10-22/),
    ).toBeInTheDocument()
    const link = screen.getByRole('link', {
      name: 'Open original (greenhouse-acme)',
    })
    expect(link).toHaveAttribute('href', 'https://example.org/jobs/1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
    expect(link.getAttribute('rel')).toContain('noreferrer')
  })

  it('shows one link per source', async () => {
    stubFetch(() =>
      json(200, [
        posting({
          links: [
            {
              sourceId: 'greenhouse-acme',
              url: 'https://example.org/1',
              primary: true,
            },
            {
              sourceId: 'other-board',
              url: 'https://other.example/2',
              primary: false,
            },
          ],
        }),
      ]),
    )
    mount('/postings')

    expect(
      await screen.findAllByRole('link', { name: /Open original/ }),
    ).toHaveLength(2)
    expect(
      screen.getByRole('link', { name: 'Open original (other-board)' }),
    ).toBeInTheDocument()
  })

  it('shows an empty message and no list', async () => {
    stubFetch(() => json(200, []))
    mount('/postings')

    expect(await screen.findByText('No postings match.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('renders no anchor for an unsafe link', async () => {
    stubFetch(() =>
      json(200, [
        posting({
          links: [{ sourceId: 's', url: 'javascript:alert(1)', primary: true }],
        }),
      ]),
    )
    mount('/postings')

    await screen.findByRole('link', { name: 'Backend Engineer' })
    expect(
      screen.queryByRole('link', { name: /Open original/ }),
    ).not.toBeInTheDocument()
  })

  it('shows the landing page and asks the API for nothing when signed out', () => {
    const urls = stubFetch(() => json(200, []))
    mount('/postings', fakeAuth({ status: 'signedOut' }))

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(urls).toEqual([])
  })

  it('shows a fixed message and a working retry on a server error', async () => {
    let fail = true
    stubFetch(() =>
      fail ? json(500, { secret: 'BODY' }) : json(200, [posting()]),
    )
    mount('/postings')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/BODY/)).not.toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(
      await screen.findByRole('link', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
  })
})

describe('search and paging', () => {
  it('sends the search text and resets to the first page', async () => {
    const urls = stubFetch(() => json(200, [posting()]))
    mount('/postings?page=2')
    await screen.findByRole('link', { name: 'Backend Engineer' })

    await userEvent.type(screen.getByLabelText('Search'), 'kotlin')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(listUrls(urls).at(-1)).toContain('q=kotlin'))
    expect(listUrls(urls).at(-1)).toContain('page=0')
  })

  it('sends the canton filter', async () => {
    const urls = stubFetch(() => json(200, [posting()]))
    mount('/postings')
    await screen.findByRole('link', { name: 'Backend Engineer' })

    await userEvent.selectOptions(screen.getByLabelText('Canton'), 'ZH')

    await waitFor(() => expect(listUrls(urls).at(-1)).toContain('canton=ZH'))
  })

  it('pages forward and back; previous is off on the first page and next on a short page', async () => {
    const full = Array.from({ length: 20 }, (_, i) =>
      posting({ id: `id-${i}`, title: `Job ${i}` }),
    )
    const urls = stubFetch((url) =>
      url.includes('page=1')
        ? json(200, [posting({ title: 'Last one' })])
        : json(200, full),
    )
    mount('/postings')
    await screen.findByRole('link', { name: 'Job 0' })

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(
      await screen.findByRole('link', { name: 'Last one' }),
    ).toBeInTheDocument()
    expect(listUrls(urls).at(-1)).toContain('page=1')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(
      await screen.findByRole('link', { name: 'Job 0' }),
    ).toBeInTheDocument()
  })
})

describe('the detail page', () => {
  const detail = (over: Partial<PostingDetail> = {}): PostingDetail => ({
    ...posting(),
    description: 'Line one\nLine two',
    ...over,
  })

  it('shows the description as text and the links', async () => {
    stubFetch(() =>
      json(
        200,
        detail({ description: '<script>alert(1)</script> and <b>bold</b>' }),
      ),
    )
    const { container } = mount(
      '/postings/11111111-1111-1111-1111-111111111111',
    )

    expect(
      await screen.findByRole('heading', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/<script>alert\(1\)<\/script> and <b>bold<\/b>/),
    ).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(
      screen.getByRole('link', { name: 'Open original (greenhouse-acme)' }),
    ).toBeInTheDocument()
  })

  it('shows not found with a way back', async () => {
    stubFetch(() => json(404, {}))
    mount('/postings/nope')

    expect(
      await screen.findByRole('heading', { name: 'Posting not found' }),
    ).toBeInTheDocument()
    expect(
      // The page renders inside the layout's `main`, which this test does not mount.
      screen.getByRole('link', { name: 'Back to the postings' }),
    ).toHaveAttribute('href', '/postings')
  })
})

describe('the Interested action', () => {
  const APP_ID = '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b'
  const POSTING_ID = '11111111-1111-1111-1111-111111111111'
  const summary = {
    id: APP_ID,
    postingId: POSTING_ID,
    title: 'Backend Engineer',
    company: 'Acme',
    stage: 'INTERESTED',
    appliedOn: null,
    stageChangedAt: '2026-10-23T10:00:00Z',
    liveStatus: 'OPEN',
  }

  /** Answers the stage lookup with `tracked` and the track call with `track`. */
  function stubTracking(
    tracked: unknown[],
    track: () => Response = () => json(201, summary),
  ) {
    const methods: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit = {}) => {
        methods.push(init.method ?? 'GET')
        if (url.startsWith('/api/applications/by-posting'))
          return json(200, tracked)
        if (init.method === 'POST') return track()
        if (url.startsWith('/api/postings/'))
          return json(200, { ...posting(), description: 'Invented text' })
        return json(200, [posting()])
      }),
    )
    return methods
  }

  it('marks a posting interested from the list and shows the stage with a link', async () => {
    const methods = stubTracking([])
    mount('/postings')

    await userEvent.click(
      await screen.findByRole('button', { name: 'Interested' }),
    )

    expect(await screen.findByText(/Stage: Interested/)).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Open application' }),
    ).toHaveAttribute('href', `/applications/${APP_ID}`)
    expect(
      screen.queryByRole('button', { name: 'Interested' }),
    ).not.toBeInTheDocument()
    expect(methods).toContain('POST')
  })

  it('marks a posting interested on its page', async () => {
    stubTracking([])
    mount(`/postings/${POSTING_ID}`)

    await userEvent.click(
      await screen.findByRole('button', { name: 'Interested' }),
    )

    expect(await screen.findByText(/Stage: Interested/)).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Open application' }),
    ).toHaveAttribute('href', `/applications/${APP_ID}`)
  })

  it('shows the stage of a posting that is already tracked from the start', async () => {
    stubTracking([
      { postingId: POSTING_ID, applicationId: APP_ID, stage: 'APPLIED' },
    ])
    mount('/postings')

    expect(await screen.findByText(/Stage: Applied/)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Interested' }),
    ).not.toBeInTheDocument()
  })

  it('shows a message and keeps the posting as it was when tracking fails', async () => {
    stubTracking([], () => json(500, { secret: 'SECRET-BODY' }))
    mount('/postings')

    await userEvent.click(
      await screen.findByRole('button', { name: 'Interested' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Interested' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Stage:/)).not.toBeInTheDocument()
  })
})
