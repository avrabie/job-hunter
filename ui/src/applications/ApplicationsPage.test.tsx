import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { ApplicationsPage } from './ApplicationsPage.tsx'
import type { ApplicationSummary } from './applicationsApi.ts'

const app = (over: Partial<ApplicationSummary> = {}): ApplicationSummary => ({
  id: '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b',
  postingId: '11111111-1111-1111-1111-111111111111',
  title: 'Backend Engineer',
  company: 'Employer1 AG',
  stage: 'INTERESTED',
  appliedOn: null,
  stageChangedAt: '2026-10-23T10:00:00Z',
  liveStatus: 'OPEN',
  ...over,
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

function stubFetch(
  handler: (url: string, init: RequestInit) => Response | Promise<Response>,
) {
  const calls: { url: string; init: RequestInit }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, init })
      return handler(url, init)
    }),
  )
  return calls
}

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/applications']}>
        <Routes>
          <Route path="/applications" element={<ApplicationsPage />} />
          <Route path="/applications/:id" element={<p>Detail page</p>} />
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

const region = (name: string) => screen.getByRole('region', { name })

describe('Job Tracking page', () => {
  it('groups the applications under their stage', async () => {
    stubFetch(() =>
      json(200, [
        app({ id: 'a', title: 'Job A', stage: 'INTERESTED' }),
        app({ id: 'b', title: 'Job B', stage: 'APPLYING' }),
        app({ id: 'c', title: 'Job C', stage: 'APPLIED' }),
      ]),
    )
    mount()

    expect(
      await screen.findByRole('link', { name: 'Job A' }),
    ).toBeInTheDocument()
    expect(
      within(region('Interested')).getByRole('link', { name: 'Job A' }),
    ).toBeInTheDocument()
    expect(
      within(region('Applying')).getByRole('link', { name: 'Job B' }),
    ).toBeInTheDocument()
    expect(
      within(region('Applied')).getByRole('link', { name: 'Job C' }),
    ).toBeInTheDocument()
    expect(within(region('Rejected')).getByText('None.')).toBeInTheDocument()
  })

  it('shows company, dates and a link to the application', async () => {
    stubFetch(() =>
      json(200, [app({ appliedOn: '2026-10-24', stage: 'APPLIED' })]),
    )
    mount()

    expect(
      await screen.findByText(
        'Employer1 AG · stage since 2026-10-23 · applied 2026-10-24',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Backend Engineer' }),
    ).toHaveAttribute(
      'href',
      '/applications/0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b',
    )
  })

  it('shows a warning mark when the posting changed or is gone, and none when it is open', async () => {
    stubFetch(() =>
      json(200, [
        app({ id: 'a', title: 'Open job', liveStatus: 'OPEN' }),
        app({ id: 'b', title: 'Changed job', liveStatus: 'CHANGED' }),
        app({ id: 'c', title: 'Gone job', liveStatus: 'GONE' }),
      ]),
    )
    mount()

    await screen.findByRole('link', { name: 'Open job' })
    expect(within(region('Interested')).getAllByText(/^Warning:/)).toHaveLength(
      2,
    )
    expect(
      screen.getByText('Warning: the posting changed after you marked it.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Warning: the posting is no longer listed.'),
    ).toBeInTheDocument()
  })

  it('changes the stage from the row and moves the row to its new group', async () => {
    const calls = stubFetch((_url, init) =>
      init.method === 'PATCH' ? json(200, {}) : json(200, [app()]),
    )
    mount()
    await screen.findByRole('link', { name: 'Backend Engineer' })

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Stage of Backend Engineer' }),
      'APPLIED',
    )

    await waitFor(() =>
      expect(
        within(region('Applied')).getByRole('link', {
          name: 'Backend Engineer',
        }),
      ).toBeInTheDocument(),
    )
    const patch = calls.find((c) => c.init.method === 'PATCH')
    expect(patch?.url).toBe(
      '/api/applications/0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b',
    )
    expect(JSON.parse(patch?.init.body as string)).toEqual({
      stage: 'APPLIED',
    })
  })

  it('keeps the row in its stage and shows a fixed message when the change fails', async () => {
    stubFetch((_url, init) =>
      init.method === 'PATCH'
        ? json(500, { secret: 'SECRET-BODY' })
        : json(200, [app()]),
    )
    mount()
    await screen.findByRole('link', { name: 'Backend Engineer' })

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Stage of Backend Engineer' }),
      'REJECTED',
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
    expect(
      within(region('Interested')).getByRole('link', {
        name: 'Backend Engineer',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: 'Stage of Backend Engineer' }),
    ).toHaveValue('INTERESTED')
  })

  it('says how to start when there are no applications', async () => {
    stubFetch(() => json(200, []))
    mount()

    expect(
      await screen.findByText(/You have not marked any postings yet/),
    ).toBeInTheDocument()
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('shows the landing page and asks the API for nothing when signed out', () => {
    const calls = stubFetch(() => json(200, []))
    mount(fakeAuth({ status: 'signedOut' }))

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(calls).toEqual([])
  })

  it('shows a fixed message and a working retry on a server error', async () => {
    let fail = true
    stubFetch(() => (fail ? json(500, { secret: 'BODY' }) : json(200, [app()])))
    mount()

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
