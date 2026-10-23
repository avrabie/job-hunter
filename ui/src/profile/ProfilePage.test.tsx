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

/**
 * Stubs fetch for `/api/profile`; `calls` records the method and parsed body of each request. The file cabinet of the page
 * asks `/api/files` too: that is answered "no files" here and not recorded (its own tests are in `files/FileCabinet.test.tsx`).
 */
function stubApi(handler: Handler) {
  const calls: { method: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/files')) return new Response('', { status: 404 })
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

describe('publications', () => {
  const pub = (title: string, year: number) => ({
    title,
    venue: null,
    year,
    authors: null,
    url: null,
  })

  it('adds a publication with a title and year and sends empty fields as null', async () => {
    const calls = stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(200, {
            version: 1,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      screen.getByRole('button', { name: 'Add publication' }),
    )
    await userEvent.type(screen.getByLabelText('Title'), 'Fast Joins')
    await userEvent.type(screen.getByLabelText('Year'), '2021')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 1).')).toBeVisible()
    const put = calls.find((c) => c.method === 'PUT')!
    const sent = put.body as { profile: Profile }
    expect(sent.profile.publications).toEqual([
      {
        title: 'Fast Joins',
        venue: null,
        year: 2021,
        authors: null,
        url: null,
      },
    ])
  })

  it('names an unsafe link on leaving it, and shows the server message when saved anyway', async () => {
    const calls = stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(400, {
            error: 'validation_failed',
            fields: {
              'publications[0].url': 'must be an http or https address',
            },
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      screen.getByRole('button', { name: 'Add publication' }),
    )
    await userEvent.type(screen.getByLabelText('Title'), 'Paper')
    await userEvent.type(screen.getByLabelText('Link'), 'javascript:alert(1)')
    await userEvent.tab()
    expect(
      screen.getByText('Link: must be an http or https address'),
    ).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(1)
    const label = (
      await screen.findAllByText('Link: must be an http or https address')
    )[0]!.closest('label')!
    expect(within(label).getByRole('textbox')).toHaveValue(
      'javascript:alert(1)',
    )
  })

  it('moves and removes publications', async () => {
    const calls = stubApi((method, body) =>
      method === 'GET'
        ? json(200, {
            version: 1,
            profile: {
              ...emptyProfile(),
              publications: [
                pub('First', 2019),
                pub('Second', 2020),
                pub('Third', 2021),
              ],
            },
          })
        : json(200, {
            version: 2,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      screen.getByRole('button', { name: 'Move Publication 2 up' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Remove Publication 3' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    const sent = calls.find((c) => c.method === 'PUT')!.body as {
      profile: Profile
    }
    expect(sent.profile.publications.map((p) => p.title)).toEqual([
      'Second',
      'First',
    ])
  })
})

describe('field checks while editing', () => {
  async function open() {
    const calls = stubApi(() =>
      json(200, { version: 0, profile: emptyProfile() }),
    )
    mount()
    await screen.findByLabelText('City')
    return calls
  }

  it('shows a message when a field is left, without saving', async () => {
    const calls = await open()
    await userEvent.type(screen.getByLabelText('Canton'), 'zh')
    expect(
      screen.queryByText(/must be two uppercase letters/),
    ).not.toBeInTheDocument()
    await userEvent.tab()
    expect(
      screen.getByText('Canton: must be two uppercase letters'),
    ).toBeVisible()
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
  })

  it('clears the message while typing a valid value', async () => {
    await open()
    const canton = screen.getByLabelText('Canton')
    await userEvent.type(canton, 'zh')
    await userEvent.tab()
    await userEvent.clear(canton)
    await userEvent.type(canton, 'ZH')
    expect(screen.queryByText(/Canton: must be/)).not.toBeInTheDocument()
  })

  it('does not scold a field that was left valid or never visited', async () => {
    await open()
    await userEvent.type(screen.getByLabelText('City'), 'Bern')
    await userEvent.tab()
    await userEvent.click(screen.getByRole('button', { name: 'Add position' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the server message when the browser found no problem', async () => {
    stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(400, {
            error: 'validation_failed',
            fields: { summary: 'must be at most 4000 characters' },
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(
      await screen.findByText('Summary: must be at most 4000 characters'),
    ).toBeVisible()
  })

  it('focuses the first field with a message after a failed save', async () => {
    stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(400, {
            error: 'validation_failed',
            fields: {
              'experience[0].title': 'must not be blank',
              'location.canton': 'must be two uppercase letters',
            },
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(screen.getByRole('button', { name: 'Add position' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Canton: must be two uppercase letters')
    expect(
      document.querySelector('[data-path="location.canton"]'),
    ).toHaveFocus()
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
