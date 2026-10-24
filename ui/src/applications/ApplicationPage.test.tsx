import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { ApplicationPage } from './ApplicationPage.tsx'
import type { ApplicationDetail } from './applicationsApi.ts'

// The letter workspace belongs to another change; here only the id it is given matters.
vi.mock('./LetterWorkspace.tsx', async () => {
  const { createElement } =
    await vi.importActual<typeof import('react')>('react')
  return {
    LetterWorkspace: (props: { applicationId: string }) =>
      createElement('p', { 'data-testid': 'letters' }, props.applicationId),
  }
})

const ID = '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b'

const detail = (over: Partial<ApplicationDetail> = {}): ApplicationDetail => ({
  id: ID,
  postingId: '11111111-1111-1111-1111-111111111111',
  title: 'Backend Engineer',
  company: 'Employer1 AG',
  stage: 'INTERESTED',
  appliedOn: null,
  stageChangedAt: '2026-10-23T10:00:00Z',
  liveStatus: 'OPEN',
  url: 'https://example.org/jobs/1',
  description: 'Line one\n\nLine two',
  capturedAt: '2026-10-22T10:00:00Z',
  postingLastSeen: '2026-10-23T10:00:00Z',
  notes: '',
  letterCount: 0,
  ...over,
})

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

/** A small in-memory stand-in for the backend: PATCH changes what GET returns afterwards. */
function fakeServer(initial: Partial<ApplicationDetail> = {}) {
  let record = detail(initial)
  const calls = stubFetch((_url, init) => {
    if (init.method === 'PATCH') {
      record = { ...record, ...JSON.parse(init.body as string) }
      return json(200, {})
    }
    if (init.method === 'DELETE') return new Response(null, { status: 204 })
    return json(200, record)
  })
  return { calls }
}

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[`/applications/${ID}`]}>
        <Routes>
          <Route path="/applications" element={<p>Job tracking list</p>} />
          <Route path="/applications/:id" element={<ApplicationPage />} />
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

describe('application page', () => {
  it('shows the frozen description as text, the title and the link to the original', async () => {
    fakeServer({
      description: '<script>alert(1)</script> and <b>bold</b>',
    })
    const { container } = mount()

    expect(
      await screen.findByRole('heading', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/<script>alert\(1\)<\/script> and <b>bold<\/b>/),
    ).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(
      screen.getByRole('link', { name: 'Open original posting' }),
    ).toHaveAttribute('href', 'https://example.org/jobs/1')
  })

  it('shows no link for an unsafe original address', async () => {
    fakeServer({ url: 'javascript:alert(1)' })
    mount()

    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(
      screen.queryByRole('link', { name: 'Open original posting' }),
    ).not.toBeInTheDocument()
  })

  it('shows the live-posting notice when the posting changed', async () => {
    fakeServer({ liveStatus: 'CHANGED' })
    mount()

    expect(
      await screen.findByText(/The posting changed after you marked it/),
    ).toBeInTheDocument()
  })

  it('shows the live-posting notice when the posting is gone', async () => {
    fakeServer({ liveStatus: 'GONE' })
    mount()

    expect(
      await screen.findByText(/The posting is no longer listed/),
    ).toBeInTheDocument()
  })

  it('shows no live-posting notice while the posting is open', async () => {
    fakeServer({ liveStatus: 'OPEN' })
    mount()

    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(screen.queryByText(/The posting changed/)).not.toBeInTheDocument()
    expect(screen.queryByText(/no longer listed/)).not.toBeInTheDocument()
  })

  it('saves the notes and shows them again after a reload', async () => {
    const { calls } = fakeServer()
    const first = mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.type(screen.getByLabelText('Notes'), 'Call on Monday')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved.')).toBeInTheDocument()
    const patch = calls.find((c) => c.init.method === 'PATCH')
    expect(JSON.parse(patch?.init.body as string)).toMatchObject({
      notes: 'Call on Monday',
    })

    first.unmount()
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(screen.getByLabelText('Notes')).toHaveValue('Call on Monday')
  })

  it('saves the applied date and clears it when emptied', async () => {
    const { calls } = fakeServer()
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.type(screen.getByLabelText('Applied on'), '2026-10-24')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Saved.')
    let patch = calls.filter((c) => c.init.method === 'PATCH').at(-1)
    expect(JSON.parse(patch?.init.body as string)).toMatchObject({
      appliedOn: '2026-10-24',
    })

    await userEvent.clear(screen.getByLabelText('Applied on'))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() =>
      expect(calls.filter((c) => c.init.method === 'PATCH')).toHaveLength(2),
    )
    patch = calls.filter((c) => c.init.method === 'PATCH').at(-1)
    expect(JSON.parse(patch?.init.body as string)).toMatchObject({
      appliedOn: null,
    })
  })

  it('shows a fixed message when saving fails and keeps what was typed', async () => {
    stubFetch((_url, init) =>
      init.method === 'PATCH'
        ? json(400, { error: 'notes_too_long', secret: 'SECRET-BODY' })
        : json(200, detail()),
    )
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.type(screen.getByLabelText('Notes'), 'Draft')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The stage, notes or date were not accepted.',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Notes')).toHaveValue('Draft')
  })

  it('changes the stage and shows the new one', async () => {
    const { calls } = fakeServer()
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.selectOptions(screen.getByLabelText('Stage'), 'APPLIED')

    expect(await screen.findByDisplayValue('Applied')).toBeInTheDocument()
    expect(
      JSON.parse(
        calls.find((c) => c.init.method === 'PATCH')?.init.body as string,
      ),
    ).toEqual({ stage: 'APPLIED' })
  })

  it('removes the application only after a confirmation and goes back to the list', async () => {
    const { calls } = fakeServer()
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.click(
      screen.getByRole('button', { name: 'Remove application' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(calls.some((c) => c.init.method === 'DELETE')).toBe(false)
    expect(
      screen.getByRole('button', { name: 'Remove application' }),
    ).toBeInTheDocument()

    await userEvent.click(
      screen.getByRole('button', { name: 'Remove application' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))

    expect(await screen.findByText('Job tracking list')).toBeInTheDocument()
    const del = calls.find((c) => c.init.method === 'DELETE')
    expect(del?.url).toBe(`/api/applications/${ID}`)
  })

  it('shows a fixed message and stays when removing fails', async () => {
    stubFetch((_url, init) =>
      init.method === 'DELETE'
        ? json(500, { secret: 'SECRET-BODY' })
        : json(200, detail()),
    )
    mount()
    await screen.findByRole('heading', { name: 'Backend Engineer' })

    await userEvent.click(
      screen.getByRole('button', { name: 'Remove application' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Backend Engineer' }),
    ).toBeInTheDocument()
  })

  it('shows not found with a way back for another user or an unknown id', async () => {
    stubFetch(() => json(404, {}))
    mount()

    expect(
      await screen.findByRole('heading', { name: 'Application not found' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Back to job tracking' }),
    ).toHaveAttribute('href', '/applications')
  })

  it('gives the letter workspace this application id', async () => {
    fakeServer()
    mount()

    expect(await screen.findByTestId('letters')).toHaveTextContent(ID)
  })

  it('shows the landing page and asks the API for nothing when signed out', () => {
    const calls = stubFetch(() => json(200, detail()))
    mount(fakeAuth({ status: 'signedOut' }))

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(calls).toEqual([])
  })
})
