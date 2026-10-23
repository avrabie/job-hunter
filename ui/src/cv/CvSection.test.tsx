import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { ProfilePage } from '../profile/ProfilePage.tsx'
import { emptyProfile, type Profile } from '../profile/types.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'

const cvInfo = {
  fileName: 'Jane CV.pdf',
  contentType: 'application/pdf',
  sizeBytes: 20480,
  uploadedAt: '2026-10-23T10:00:00Z',
}

type Call = { method: string; url: string; body: unknown; auth: string | null }
type Route = (call: Call) => Response | undefined

const json = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status })

/** Routes `/api/profile` and `/api/cv...`; `extra` answers first. Returns the recorded calls. */
function stubApi(extra: Route, profile: Profile = emptyProfile()) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const call: Call = {
        method: init?.method ?? 'GET',
        url,
        body:
          init?.body instanceof FormData
            ? 'form-data'
            : init?.body
              ? JSON.parse(init.body as string)
              : undefined,
        auth: new Headers(init?.headers).get('Authorization'),
      }
      calls.push(call)
      return (
        extra(call) ??
        (url === '/api/profile'
          ? json(200, { version: 1, profile })
          : json(404))
      )
    }),
  )
  return calls
}

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/profile']}>
        <ProfilePage />
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

const hasCv = (c: Call) =>
  c.url === '/api/cv' && c.method === 'GET' ? json(200, cvInfo) : undefined

describe('without a CV', () => {
  it('offers an upload and shows no file details', async () => {
    stubApi(() => undefined)
    mount()
    expect(await screen.findByLabelText('Upload a CV')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download' })).toBeNull()
    expect(
      screen.queryByRole('button', { name: /Fill the profile/ }),
    ).toBeNull()
  })

  it('shows the details after an upload', async () => {
    const calls = stubApi((c) =>
      c.method === 'POST' && c.url === '/api/cv'
        ? json(200, cvInfo)
        : undefined,
    )
    mount()
    await userEvent.upload(
      await screen.findByLabelText('Upload a CV'),
      new File(['x'], 'Jane CV.pdf', { type: 'application/pdf' }),
    )
    expect(await screen.findByText('Jane CV.pdf')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST')?.body).toBe('form-data')
  })

  it('shows a rejection and keeps the page usable', async () => {
    stubApi((c) =>
      c.method === 'POST' ? json(413, { message: 'SERVER' }) : undefined,
    )
    mount()
    await userEvent.upload(
      await screen.findByLabelText('Upload a CV'),
      new File(['x'], 'big.txt'),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The file is larger than 5 MB.',
    )
    expect(screen.getByLabelText('Upload a CV')).toBeInTheDocument()
  })
})

describe('with a CV', () => {
  it('shows name, size and date, and keeps the earlier CV when a replacement is rejected', async () => {
    stubApi((c) => (c.method === 'POST' ? json(400) : hasCv(c)))
    mount()
    expect(await screen.findByText('Jane CV.pdf')).toBeInTheDocument()
    expect(screen.getByText(/20 KB/)).toBeInTheDocument()
    await userEvent.upload(
      screen.getByLabelText('Replace CV'),
      new File(['x'], 'x.txt'),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('must be a PDF')
    expect(screen.getByText('Jane CV.pdf')).toBeInTheDocument()
  })

  it('downloads under the stored name using the user token', async () => {
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})
    Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    })
    let name = ''
    click.mockImplementation(function (this: HTMLAnchorElement) {
      name = this.download
    })
    const calls = stubApi((c) =>
      c.url === '/api/cv/file' ? new Response('bytes') : hasCv(c),
    )
    mount()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Download' }),
    )
    await waitFor(() => expect(name).toBe('Jane CV.pdf'))
    expect(calls.find((c) => c.url === '/api/cv/file')?.auth).toMatch(
      /^Bearer /,
    )
  })

  it('deletes after confirmation and returns to the upload state', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let deleted = false
    stubApi((c) => {
      if (c.method === 'DELETE') {
        deleted = true
        return new Response(null, { status: 204 })
      }
      return deleted ? undefined : hasCv(c)
    })
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(await screen.findByLabelText('Upload a CV')).toBeInTheDocument()
    expect(screen.queryByText('Jane CV.pdf')).toBeNull()
  })

  it('does nothing when the deletion is not confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const calls = stubApi(hasCv)
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    expect(screen.getByText('Jane CV.pdf')).toBeInTheDocument()
  })
})

describe('filling the profile from the CV', () => {
  const draft = {
    ...emptyProfile(),
    summary: 'Drafted from the CV',
    skills: [{ name: 'Kotlin', level: null }],
  }

  it('puts the draft in the form as unsaved changes and never saves', async () => {
    const calls = stubApi((c) =>
      c.url === '/api/cv/extract' ? json(200, draft) : hasCv(c),
    )
    mount()
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )

    expect(
      await screen.findByDisplayValue('Drafted from the CV'),
    ).toBeInTheDocument()
    expect(screen.getByDisplayValue('Kotlin')).toBeInTheDocument()
    expect(
      screen.getByText(/Nothing is saved until you press Save/),
    ).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('keeps the preferences of the user', async () => {
    const mine = {
      ...emptyProfile(),
      preferences: {
        ...emptyProfile().preferences,
        targetRoles: ['Staff Engineer'],
      },
    }
    stubApi(
      (c) => (c.url === '/api/cv/extract' ? json(200, draft) : hasCv(c)),
      mine,
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mount()
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    await screen.findByDisplayValue('Drafted from the CV')
    expect(screen.getByDisplayValue('Staff Engineer')).toBeInTheDocument()
  })

  it('asks before replacing a form that already has content', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const existing = { ...emptyProfile(), summary: 'My own summary' }
    const calls = stubApi(
      (c) => (c.url === '/api/cv/extract' ? json(200, draft) : hasCv(c)),
      existing,
    )
    mount()
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    expect(confirm).toHaveBeenCalled()
    expect(calls.some((c) => c.url === '/api/cv/extract')).toBe(false)
    expect(screen.getByDisplayValue('My own summary')).toBeInTheDocument()
  })

  it.each([
    [422, 'No text could be read from the CV'],
    [429, 'Too many extractions. Please try again later.'],
    [502, 'The CV could not be read right now.'],
  ])('shows a fixed message for %i', async (status, text) => {
    stubApi((c) => (c.url === '/api/cv/extract' ? json(status) : hasCv(c)))
    mount()
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })
})

describe('privacy', () => {
  it('keeps the file name and details out of browser storage and the console', async () => {
    stubApi(hasCv)
    mount()
    await screen.findByText('Jane CV.pdf')
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain('Jane CV')
    for (const m of ['log', 'info', 'warn', 'error', 'debug'] as const)
      expect(JSON.stringify(vi.mocked(console[m]).mock.calls)).not.toContain(
        'Jane CV',
      )
  })
})
