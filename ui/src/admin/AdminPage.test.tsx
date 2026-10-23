import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { Shell } from '../pages/Shell.tsx'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { AdminPage } from './AdminPage.tsx'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

const enabled = {
  enabled: true,
  cron: '0 0 * * * *',
  zone: 'Europe/Zurich',
  nextFire: '2026-10-23T11:00:00Z',
  updatedAt: '2026-10-22T10:00:00Z',
}

function stubFetch(handler: (url: string, init?: RequestInit) => Response) {
  const calls: { url: string; init?: RequestInit }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, init })
      return handler(url, init)
    }),
  )
  return calls
}

const mountAdmin = (auth: AuthApi = fakeAuth(signedIn())) =>
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )

const tokenWith = (roles: string[]) =>
  `h.${btoa(JSON.stringify({ roles })).replace(/=+$/, '')}.s`

let logs: ReturnType<typeof vi.spyOn>[]
beforeEach(() => {
  logs = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('the schedule', () => {
  it('shows an enabled schedule with its next run', async () => {
    stubFetch(() => json(200, enabled))
    mountAdmin()
    expect(await screen.findByText(/Crawling is on/)).toHaveTextContent(
      '2026-10-23T11:00:00Z',
    )
    expect(screen.getByLabelText(/Cron/)).toHaveValue('0 0 * * * *')
    expect(screen.getByLabelText('Time zone')).toHaveValue('Europe/Zurich')
  })

  it('says crawling is off and shows no next run when disabled', async () => {
    stubFetch(() => json(200, { ...enabled, enabled: false, nextFire: null }))
    mountAdmin()
    expect(await screen.findByText(/Crawling is off/)).not.toHaveTextContent(
      'Next run',
    )
  })

  it('shows the administrators-only message and no form on 403', async () => {
    stubFetch(() => json(403, {}))
    mountAdmin()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'for administrators',
    )
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull()
  })

  it('shows an error with retry when the service fails', async () => {
    let n = 0
    stubFetch(() => (n++ === 0 ? json(503, {}) : json(200, enabled)))
    mountAdmin()
    expect(await screen.findByRole('alert')).toHaveTextContent('unavailable')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText(/Crawling is on/)).toBeInTheDocument()
  })

  it('shows the landing page and requests nothing when signed out', () => {
    const calls = stubFetch(() => json(200, enabled))
    mountAdmin(fakeAuth({ status: 'signedOut' }))
    expect(screen.queryByText('Crawl schedule')).toBeNull()
    expect(calls).toHaveLength(0)
  })
})

describe('changing the schedule', () => {
  it('saves and shows the stored schedule', async () => {
    const calls = stubFetch((_, init) =>
      init?.method === 'POST'
        ? json(200, { ...enabled, cron: '0 0 */2 * * *' })
        : json(200, enabled),
    )
    mountAdmin()
    const cron = await screen.findByLabelText(/Cron/)
    await userEvent.clear(cron)
    await userEvent.type(cron, '0 0 */2 * * *')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() =>
      expect(screen.getByLabelText(/Cron/)).toHaveValue('0 0 */2 * * *'),
    )
    const post = calls.find((c) => c.init?.method === 'POST')!
    expect(JSON.parse(post.init!.body as string)).toEqual({
      enabled: true,
      cron: '0 0 */2 * * *',
      zone: 'Europe/Zurich',
    })
  })

  it('shows the rejection and keeps what was typed', async () => {
    stubFetch((_, init) =>
      init?.method === 'POST'
        ? json(400, {
            error: 'validation_failed',
            field: 'cron',
            message: 'The cron fires more often than every 15 minutes.',
          })
        : json(200, enabled),
    )
    mountAdmin()
    const cron = await screen.findByLabelText(/Cron/)
    await userEvent.clear(cron)
    await userEvent.type(cron, '* * * * * *')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'more often than every 15 minutes',
    )
    expect(screen.getByLabelText(/Cron/)).toHaveValue('* * * * * *')
  })

  it('writes no token to the console, the DOM or storage', async () => {
    stubFetch(() => json(200, enabled))
    const auth = fakeAuth(signedIn(), { getAccessToken: () => 'SECRET-TOKEN' })
    const { container } = mountAdmin(auth)
    await screen.findByText(/Crawling is on/)
    expect(container.innerHTML).not.toContain('SECRET-TOKEN')
    expect(JSON.stringify(logs.map((l) => l.mock.calls))).not.toContain(
      'SECRET-TOKEN',
    )
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain('SECRET-TOKEN')
  })
})

describe('the Admin link in the shell', () => {
  const mountShell = (roles: string[]) => {
    stubFetch(() => json(200, {}))
    render(
      <AuthContext.Provider
        value={fakeAuth(signedIn(), { getAccessToken: () => tokenWith(roles) })}
      >
        <MemoryRouter>
          <Shell identity="a@example.org" />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
  }

  it('is shown to an administrator', async () => {
    mountShell(['USER', 'ADMIN'])
    expect(await screen.findByRole('link', { name: 'Admin' })).toHaveAttribute(
      'href',
      '/admin',
    )
  })

  it('is hidden from an ordinary user', async () => {
    mountShell(['USER'])
    await screen.findByRole('link', { name: 'Postings' })
    expect(screen.queryByRole('link', { name: 'Admin' })).toBeNull()
  })
})
