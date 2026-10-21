import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import fixture from './fixtures/full-profile.json'
import { ProfilePage } from './ProfilePage.tsx'
import { emptyProfile, type Profile } from './types.ts'

const full = fixture as unknown as Profile

type Handler = (method: string, body: unknown) => Response

/** Stubs fetch for `/api/profile`; `calls` records the method and parsed body of each request. */
function stubApi(handler: Handler) {
  const calls: { method: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const body = init?.body ? JSON.parse(init.body as string) : undefined
      calls.push({ method, body })
      return handler(method, body)
    }),
  )
  return calls
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  const view = render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/profile']}>
        <ProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  return { auth, ...view }
}

let consoleSpies: ReturnType<typeof vi.spyOn>[] = []

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('loading', () => {
  it('shows the stored profile', async () => {
    stubApi(() => json(200, { version: 2, profile: full }))
    mount()
    expect(await screen.findByLabelText('City')).toHaveValue('Zurich')
    expect(screen.getByLabelText('Summary')).toHaveValue(
      'Invented engineer used only in tests.',
    )
    expect(screen.getAllByLabelText('Employer')).toHaveLength(2)
    expect(screen.getAllByLabelText('Employer')[0]).toHaveValue('Example AG')
    expect(screen.getByLabelText('Work authorisation')).toHaveValue('PERMIT_B')
    expect(screen.getByLabelText('HYBRID')).toBeChecked()
    expect(screen.getByLabelText('ONSITE')).not.toBeChecked()
  })

  it('shows empty sections at version 0', async () => {
    stubApi(() => json(200, { version: 0, profile: emptyProfile() }))
    mount()
    expect(await screen.findByLabelText('City')).toHaveValue('')
    expect(screen.queryByLabelText('Employer')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add position' })).toBeVisible()
  })

  it('shows a failure and lets the user try again', async () => {
    let answer = 500
    stubApi(() =>
      answer === 200
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(answer, {}),
    )
    mount()
    expect(
      await screen.findByText('The request failed (status 500).'),
    ).toBeVisible()
    answer = 200
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByLabelText('City')).toBeVisible()
  })
})

describe('signed-out visitor', () => {
  it('shows the landing page, signs in only on request, and returns to /profile', async () => {
    const calls = stubApi(() => json(200, {}))
    const { auth } = mount(fakeAuth({ status: 'signedOut' }))
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Register' })).toBeVisible()
    expect(auth.signIn).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledWith('/profile')
    expect(calls).toHaveLength(0)
  })
})

describe('editing lists', () => {
  it('adds an entry, fills it, removes another, and saves exactly that', async () => {
    const calls = stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 2, profile: full })
        : json(200, {
            version: 3,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await screen.findByLabelText('City')

    await userEvent.click(
      screen.getByRole('button', { name: 'Remove Position 2' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Add position' }))
    const employers = screen.getAllByLabelText('Employer')
    expect(employers).toHaveLength(2)
    await userEvent.type(employers[1]!, 'Neue AG')
    await userEvent.type(screen.getAllByLabelText('Title')[1]!, 'Lead')
    await userEvent.type(screen.getAllByLabelText('Start')[1]!, '2022-05')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 3).')).toBeVisible()
    const put = calls.find((c) => c.method === 'PUT')!
    const sent = put.body as { version: number; profile: Profile }
    expect(sent.version).toBe(2)
    expect(sent.profile.experience.map((e) => e.employer)).toEqual([
      'Example AG',
      'Neue AG',
    ])
    expect(sent.profile.experience[1]).toMatchObject({
      title: 'Lead',
      start: '2022-05',
      end: null,
    })
  })

  it('reorders entries', async () => {
    stubApi(() => json(200, { version: 1, profile: full }))
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      screen.getByRole('button', { name: 'Move Position 2 up' }),
    )
    expect(screen.getAllByLabelText('Employer')[0]).toHaveValue('Beispiel GmbH')
  })
})

describe('save results', () => {
  it('shows each server message next to its field and keeps the other edits', async () => {
    stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(400, {
            error: 'validation_failed',
            fields: { 'experience[0].title': 'must not be blank' },
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.type(screen.getByLabelText('City'), 'Bern')
    await userEvent.click(screen.getByRole('button', { name: 'Add position' }))
    await userEvent.type(screen.getByLabelText('Employer'), 'Acme')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    const label = (await screen.findByText('Title: must not be blank')).closest(
      'label',
    )!
    expect(within(label).getByRole('textbox')).toBeVisible()
    expect(screen.getByLabelText('City')).toHaveValue('Bern')
    expect(screen.getByLabelText('Employer')).toHaveValue('Acme')
    expect(screen.getByText('Please fix the marked fields.')).toBeVisible()
  })

  it('on a conflict keeps the edits, pauses saving, and Reload replaces them', async () => {
    let stored = {
      version: 1,
      profile: { ...emptyProfile(), summary: 'server text' },
    }
    stubApi((method) =>
      method === 'GET'
        ? json(200, stored)
        : json(409, { error: 'version_conflict' }),
    )
    mount()
    const summary = await screen.findByLabelText('Summary')
    await userEvent.type(summary, ' + my edit')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText(/changed somewhere else/)).toBeVisible()
    expect(screen.getByLabelText('Summary')).toHaveValue(
      'server text + my edit',
    )
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

    stored = {
      version: 2,
      profile: { ...emptyProfile(), summary: 'newer server text' },
    }
    await userEvent.click(screen.getByRole('button', { name: 'Reload' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Summary')).toHaveValue('newer server text'),
    )
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('shows the generic message and keeps the edits for any other failure', async () => {
    stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(500, { detail: 'SECRET-MARKER' }),
    )
    mount()
    await userEvent.type(await screen.findByLabelText('City'), 'Bern')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(
      await screen.findByText('The request failed (status 500).'),
    ).toBeVisible()
    expect(document.body).not.toHaveTextContent('SECRET-MARKER')
    expect(screen.getByLabelText('City')).toHaveValue('Bern')
  })
})

describe('privacy', () => {
  it('keeps profile text out of browser storage and the console', async () => {
    stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 1, profile: full })
        : json(200, {
            version: 2,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await userEvent.type(
      await screen.findByLabelText('Summary'),
      ' PERSONAL-MARKER',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Saved (version 2).')

    const stored = JSON.stringify({ ...localStorage, ...sessionStorage })
    for (const text of ['PERSONAL-MARKER', 'Example AG', 'Zurich']) {
      expect(stored).not.toContain(text)
      expect(document.cookie).not.toContain(text)
    }
    for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled()
  })
})

describe('unsaved edits', () => {
  function beforeUnload() {
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    return event.defaultPrevented
  }

  it('warns while there are unsaved edits and stops after a save', async () => {
    stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 1, profile: full })
        : json(200, {
            version: 2,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await screen.findByLabelText('City')
    expect(beforeUnload()).toBe(false)

    await userEvent.type(screen.getByLabelText('City'), 'x')
    expect(beforeUnload()).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Saved (version 2).')
    expect(beforeUnload()).toBe(false)
  })

  it('does not warn after the edits are undone', async () => {
    stubApi(() => json(200, { version: 1, profile: full }))
    mount()
    const city = await screen.findByLabelText('City')
    await userEvent.type(city, 'x')
    expect(beforeUnload()).toBe(true)
    await userEvent.type(city, '{Backspace}')
    expect(beforeUnload()).toBe(false)
  })

  it('asks before the Back link leaves with unsaved edits', async () => {
    stubApi(() => json(200, { version: 1, profile: full }))
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    mount()
    await userEvent.type(await screen.findByLabelText('City'), 'x')
    await userEvent.click(screen.getByRole('link', { name: 'Back' }))
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('City')).toBeVisible()
  })
})
