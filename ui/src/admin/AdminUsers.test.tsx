import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { AdminUsers } from './AdminUsers.tsx'
import type { AdminUser, AdminUserDetail } from './adminUsersApi.ts'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

const ada: AdminUser = {
  sub: 'sub-ada',
  email: 'ada@example.org',
  displayName: 'Ada',
  createdAt: '2026-11-01T10:00:00Z',
  fullName: 'Ada Example',
  hasProfile: true,
  saved: 1,
  applying: 2,
  applied: 0,
  rejected: 0,
  lastActivity: '2026-11-20T09:00:00Z',
}

const adaDetail: AdminUserDetail = {
  user: ada,
  profile: {
    fullName: 'Ada Example',
    headline: 'Kotlin engineer',
    phone: '+41 79 000 00 00',
    website: null,
    linkedin: null,
    city: 'Zürich',
    canton: 'ZH',
  },
  applications: [
    {
      id: 'a1',
      stage: 'INTERESTED',
      title: 'Backend Engineer',
      company: 'Acme',
      url: 'https://example.org/1',
      source: 'POSTING',
      capturedAt: '2026-11-10T10:00:00Z',
      stageChangedAt: '2026-11-10T10:00:00Z',
      appliedOn: null,
    },
    {
      id: 'a2',
      stage: 'APPLYING',
      title: 'Platform Engineer',
      company: 'Globex',
      url: 'https://example.org/2',
      source: 'IMPORT',
      capturedAt: '2026-11-11T10:00:00Z',
      stageChangedAt: '2026-11-12T10:00:00Z',
      appliedOn: null,
    },
  ],
}

function stubFetch(handler: (url: string) => Response) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => handler(url)),
  )
}

const mount = (auth: AuthApi = fakeAuth(signedIn())) =>
  render(
    <AuthContext.Provider value={auth}>
      <AdminUsers />
    </AuthContext.Provider>,
  )

afterEach(() => vi.unstubAllGlobals())

describe('AdminUsers', () => {
  it('lists users with their counts and opens one with its jobs and no descriptions', async () => {
    stubFetch((url) => {
      if (url.startsWith('/api/admin/users/sub-ada')) return json(200, adaDetail)
      return json(200, [ada])
    })
    mount()

    const row = (await screen.findByRole('button', { name: 'Ada Example' })).closest('tr')!
    const cells = within(row).getAllByRole('cell')
    expect(cells[2]).toHaveTextContent('1')
    expect(cells[3]).toHaveTextContent('2')

    await userEvent.click(screen.getByRole('button', { name: 'Ada Example' }))

    expect(await screen.findByText('Kotlin engineer')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Backend Engineer' })).toHaveAttribute(
      'href',
      'https://example.org/1',
    )
    expect(screen.getByRole('link', { name: 'Platform Engineer' })).toBeInTheDocument()
    expect(screen.getByText('Saved (1)')).toBeInTheDocument()
    expect(screen.getByText('Applying (1)')).toBeInTheDocument()
  })

  it('says the page is for administrators when the list is refused', async () => {
    stubFetch(() => new Response('', { status: 403 }))
    mount()

    expect(
      await screen.findByText('This page is for administrators.'),
    ).toBeInTheDocument()
  })

  it('shows a failure instead of crashing when the list has an unexpected shape', async () => {
    stubFetch(() => json(200, { enabled: true, cron: 'x', zone: 'y' }))
    mount()

    expect(
      await screen.findByText('The user list had an unexpected shape.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('table')).toBeNull())
  })
})
