import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { ImportJob } from './ImportJob.tsx'

const URL_TEXT = 'https://jobs.example.org/backend-engineer'

const draft = {
  draftId: '9d2b7c4e-1f3a-4b5c-8d6e-7f8091a2b3c4',
  title: 'Backend Engineer',
  company: 'Employer1 AG',
  location: 'Zurich',
  description: 'Invented job text for the review',
  language: 'en',
}

const imported = {
  applicationId: '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b',
  created: true,
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

type Call = { url: string; init: RequestInit }

function stubFetch(
  handler: (url: string, init: RequestInit) => Response | Promise<Response>,
) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, init })
      return handler(url, init)
    }),
  )
  return calls
}

const bodyOf = (call: Call) => JSON.parse(call.init.body as string)

/** Shows where the router ended up and the state it was given. */
function Detail() {
  const location = useLocation()
  return (
    <p data-testid="detail">
      {location.pathname}|{JSON.stringify(location.state)}
    </p>
  )
}

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/applications']}>
        <Routes>
          <Route path="/applications" element={<ImportJob />} />
          <Route path="/applications/:id" element={<Detail />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

async function previewUrl(text = URL_TEXT) {
  await userEvent.type(screen.getByLabelText(/Job posting URL/), text)
  await userEvent.click(screen.getByRole('button', { name: 'Preview' }))
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

describe('Add job from URL: preview', () => {
  it('sends the URL and shows the editable review with the shared notice', async () => {
    const calls = stubFetch(() => json(200, draft))
    mount()

    await previewUrl()

    expect(
      await screen.findByDisplayValue('Backend Engineer'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/Title/)).toHaveValue('Backend Engineer')
    expect(screen.getByLabelText(/Company/)).toHaveValue('Employer1 AG')
    expect(screen.getByLabelText(/Location/)).toHaveValue('Zurich')
    expect(screen.getByLabelText(/Description/)).toHaveValue(
      'Invented job text for the review',
    )
    expect(
      screen.getByText(
        'This job is saved only in your own job tracking; other users cannot see it.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save and track' })).toBeEnabled()
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/job-imports/preview')
    expect(calls[0].init.method).toBe('POST')
    expect(bodyOf(calls[0])).toEqual({ url: URL_TEXT })
  })

  it('does not send anything until the user presses Preview', async () => {
    const calls = stubFetch(() => json(200, draft))
    mount()

    await userEvent.type(screen.getByLabelText(/Job posting URL/), URL_TEXT)

    expect(screen.getByRole('button', { name: 'Preview' })).toBeEnabled()
    expect(calls).toEqual([])
  })
})

describe('Add job from URL: fixed messages per error code', () => {
  it.each([
    [
      'invalid_url',
      'That does not look like a web address. Check the URL and try again.',
    ],
    [
      'unsupported_site',
      "Jobs cannot be imported from this site. Use the company's own posting instead.",
    ],
    ['not_a_job', 'This page does not seem to be a single job posting.'],
    [
      'extraction_failed',
      'The job details could not be extracted from this page. Try again later.',
    ],
    [
      'llm_unavailable',
      'The import service is unavailable right now. Please try again later.',
    ],
    [
      'invalid_edit',
      'Check the title, company and description: they must not be empty, too long or contain markup.',
    ],
    ['application_limit', 'You have reached the limit of tracked jobs.'],
  ])(
    'shows the message for %s without revealing the paste field',
    async (code, text) => {
      stubFetch(() => json(422, { error: code, secret: 'SECRET-BODY' }))
      mount()

      await previewUrl()

      expect(await screen.findByRole('alert')).toHaveTextContent(text)
      expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
      expect(screen.queryByLabelText(/Job description/)).not.toBeInTheDocument()
    },
  )

  it('shows a fixed message for an unknown code, never the raw body', async () => {
    stubFetch(() =>
      json(500, { error: 'something_new', secret: 'SECRET-BODY' }),
    )
    mount()

    await previewUrl()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
  })
})

describe('Add job from URL: paste fallback', () => {
  it.each([
    [
      'unreadable',
      'The job description could not be read from this page. Paste it instead.',
    ],
    [
      'not_html',
      'This link does not lead to a web page. Paste the job description instead.',
    ],
    [
      'too_large',
      'The page is too large to read. Paste the job description instead.',
    ],
    [
      'blocked_address',
      'This address cannot be read. Paste the job description instead.',
    ],
    [
      'unreachable',
      'The page could not be reached. Paste the job description instead.',
    ],
  ])(
    'reveals the paste field after %s and resubmits the URL with the text',
    async (code, text) => {
      const calls = stubFetch((_url, init) =>
        JSON.parse(init.body as string).text === undefined
          ? json(422, { error: code })
          : json(200, draft),
      )
      mount()

      await previewUrl()

      expect(await screen.findByRole('alert')).toHaveTextContent(text)
      const paste = screen.getByLabelText(/Job description/)
      await userEvent.type(paste, 'Pasted posting text')
      await userEvent.click(screen.getByRole('button', { name: 'Preview' }))

      expect(
        await screen.findByDisplayValue('Backend Engineer'),
      ).toBeInTheDocument()
      expect(bodyOf(calls[1])).toEqual({
        url: URL_TEXT,
        text: 'Pasted posting text',
      })
    },
  )

  it('keeps the paste field and the URL when the pasted text fails too', async () => {
    stubFetch((_url, init) =>
      JSON.parse(init.body as string).text === undefined
        ? json(422, { error: 'unreadable' })
        : json(422, { error: 'not_a_job' }),
    )
    mount()

    await previewUrl()
    await userEvent.type(
      await screen.findByLabelText(/Job description/),
      'Some text',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This page does not seem to be a single job posting.',
    )
    expect(screen.getByLabelText(/Job description/)).toHaveValue('Some text')
    expect(screen.getByLabelText(/Job posting URL/)).toHaveValue(URL_TEXT)
  })
})

describe('Add job from URL: rate limit', () => {
  it('shows the fixed message and keeps the entered URL', async () => {
    stubFetch(() => json(429, { error: 'rate_limited' }))
    mount()

    await previewUrl()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many imports in a short time. Wait a few minutes and try again.',
    )
    expect(screen.getByLabelText(/Job posting URL/)).toHaveValue(URL_TEXT)
    expect(screen.queryByLabelText(/Job description/)).not.toBeInTheDocument()
  })
})

describe('Add job from URL: confirm', () => {
  it('sends the edited fields and navigates to the new application', async () => {
    const calls = stubFetch((url) =>
      url === '/api/job-imports/preview'
        ? json(200, draft)
        : json(201, imported),
    )
    mount()
    await previewUrl()
    await screen.findByDisplayValue('Backend Engineer')

    const title = screen.getByLabelText(/Title/)
    await userEvent.clear(title)
    await userEvent.type(title, 'Senior Backend Engineer')
    await userEvent.clear(screen.getByLabelText(/Location/))
    await userEvent.type(screen.getByLabelText(/Location/), 'Bern')
    await userEvent.click(
      screen.getByRole('button', { name: 'Save and track' }),
    )

    expect(await screen.findByTestId('detail')).toHaveTextContent(
      `/applications/${imported.applicationId}|{"alreadyTracked":false}`,
    )
    const confirm = calls.at(-1)
    expect(confirm?.url).toBe('/api/job-imports')
    expect(confirm?.init.method).toBe('POST')
    expect(bodyOf(confirm as Call)).toEqual({
      draftId: draft.draftId,
      title: 'Senior Backend Engineer',
      company: 'Employer1 AG',
      location: 'Bern',
      description: 'Invented job text for the review',
    })
  })

  it('passes the note on to the application when the job was already tracked', async () => {
    stubFetch((url) =>
      url === '/api/job-imports/preview'
        ? json(200, draft)
        : json(201, { ...imported, created: false }),
    )
    mount()
    await previewUrl()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Save and track' }),
    )

    expect(await screen.findByTestId('detail')).toHaveTextContent(
      `{"alreadyTracked":true}`,
    )
  })

  it('goes back to the URL step with a notice when the draft expired', async () => {
    stubFetch((url) =>
      url === '/api/job-imports/preview'
        ? json(200, draft)
        : json(404, { error: 'draft_not_found' }),
    )
    mount()
    await previewUrl()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Save and track' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This preview has expired. Preview the URL again.',
    )
    expect(screen.getByLabelText(/Job posting URL/)).toHaveValue(URL_TEXT)
    expect(
      screen.queryByRole('button', { name: 'Save and track' }),
    ).not.toBeInTheDocument()
  })

  it('stays on the review and shows a fixed message when saving fails', async () => {
    stubFetch((url) =>
      url === '/api/job-imports/preview'
        ? json(200, draft)
        : json(503, { error: 'llm_unavailable', secret: 'SECRET-BODY' }),
    )
    mount()
    await previewUrl()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Save and track' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The import service is unavailable right now. Please try again later.',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Title/)).toHaveValue('Backend Engineer')
  })
})
