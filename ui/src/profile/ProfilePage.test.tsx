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
      if (url === '/api/files') return new Response('[]', { status: 200 })
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

describe('fill a section from the CV', () => {
  const MASTER = {
    id: 'file-1',
    name: 'Synthetic CV.pdf',
    kind: 'CV',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    uploadedAt: '2026-10-23T10:00:00Z',
    master: true,
  }

  /**
   * Like `stubApi`, with a master CV chosen or not, and the section endpoint answered by `extract`. `calls` records
   * the URL and method of each request, the cabinet's own requests included.
   */
  function stubSections(master: boolean, extract: () => Response) {
    const calls: { url: string; method: string }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? 'GET'
        calls.push({ url, method })
        if (url === '/api/files/master')
          return master ? json(200, MASTER) : new Response('', { status: 404 })
        if (url === '/api/files') return json(200, master ? [MASTER] : [])
        if (url.startsWith('/api/files'))
          return new Response('', { status: 404 })
        if (url.startsWith('/api/cv/extract')) return extract()
        if (method === 'GET') return json(200, { version: 1, profile: full })
        const body = JSON.parse(init!.body as string) as { profile: Profile }
        return json(200, { version: 2, profile: body.profile })
      }),
    )
    return calls
  }

  const section = (name: string) => within(screen.getByRole('region', { name }))

  it('fills only the education entries, unsaved, and sends no profile request', async () => {
    const calls = stubSections(true, () =>
      json(200, {
        ...emptyProfile(),
        education: [
          {
            institution: 'Synthetic College',
            degree: 'BSc',
            field: null,
            start: null,
            end: null,
          },
        ],
      }),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mount()
    await screen.findByLabelText('City')
    const button = section('Education').getByRole('button', {
      name: 'Fill from CV',
    })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    expect(
      await screen.findByText(/Nothing is saved until you press Save/),
    ).toBeVisible()
    expect(section('Education').getByLabelText('Institution')).toHaveValue(
      'Synthetic College',
    )
    expect(screen.getAllByLabelText('Employer')).toHaveLength(2)
    expect(screen.getAllByLabelText('Employer')[0]).toHaveValue('Example AG')
    expect(calls).toContainEqual({
      url: '/api/cv/extract?section=education',
      method: 'POST',
    })
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
  })

  it('asks before replacing entries, and declining sends nothing', async () => {
    const calls = stubSections(true, () =>
      json(200, { ...emptyProfile(), education: [] }),
    )
    const confirm = vi
      .spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)
    mount()
    await screen.findByLabelText('City')
    const button = section('Education').getByRole('button', {
      name: 'Fill from CV',
    })
    await waitFor(() => expect(button).toBeEnabled())

    await userEvent.click(button)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(calls.some((c) => c.url.startsWith('/api/cv/extract'))).toBe(false)
    expect(section('Education').getByLabelText('Institution')).toHaveValue(
      'Example University',
    )

    await userEvent.click(button)
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(calls.some((c) => c.url.startsWith('/api/cv/extract'))).toBe(true)
    expect(section('Education').queryByLabelText('Institution')).toBeNull()
  })

  it('fills only the projects, unsaved, and sends no profile request', async () => {
    const calls = stubSections(true, () =>
      json(200, {
        ...emptyProfile(),
        projects: [
          {
            name: 'Synthetic tool',
            type: 'PERSONAL',
            context: null,
            start: null,
            end: null,
            description: null,
            technologies: ['Go'],
            results: [],
            links: [],
            highlight: false,
          },
        ],
      }),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mount()
    await screen.findByLabelText('City')
    const button = section('Projects').getByRole('button', {
      name: 'Fill from CV',
    })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    expect(
      await screen.findByText(/Nothing is saved until you press Save/),
    ).toBeVisible()
    expect(section('Projects').getByLabelText('Name')).toHaveValue(
      'Synthetic tool',
    )
    expect(screen.getAllByLabelText('Employer')).toHaveLength(2)
    expect(calls).toContainEqual({
      url: '/api/cv/extract?section=projects',
      method: 'POST',
    })
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
  })

  it('disables the four buttons with a hint when there is no master CV', async () => {
    stubSections(false, () => json(200, emptyProfile()))
    mount()
    await screen.findByLabelText('City')
    for (const name of [
      'Experience',
      'Education',
      'Publications',
      'Projects',
    ]) {
      expect(
        section(name).getByRole('button', { name: 'Fill from CV' }),
      ).toBeDisabled()
    }
    expect(
      screen.getAllByText('Choose a master CV in your files to use this.'),
    ).toHaveLength(4)
  })

  it.each([
    [
      422,
      'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
    ],
    [429, 'Too many extractions. Please try again later.'],
  ])(
    'shows the fixed message for %i in the section and keeps its entries',
    async (status, message) => {
      stubSections(true, () => json(status, {}))
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mount()
      await screen.findByLabelText('City')
      const button = section('Experience').getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      expect(await section('Experience').findByRole('alert')).toHaveTextContent(
        message,
      )
      expect(screen.getAllByLabelText('Employer')).toHaveLength(2)
      expect(screen.getAllByLabelText('Employer')[0]).toHaveValue('Example AG')
    },
  )
})

describe('Projects section', () => {
  const projects = (calls: { method: string; body: unknown }[]) =>
    (calls.find((c) => c.method === 'PUT')!.body as { profile: Profile })
      .profile.projects

  /** Echoes the saved profile back, or answers the save with a server message for the first link. */
  function stubSaves(stored: Profile, status: 200 | 400 = 200) {
    return stubApi((method, body) => {
      if (method === 'GET') return json(200, { version: 1, profile: stored })
      if (status === 400)
        return json(400, {
          error: 'validation_failed',
          fields: {
            'projects[0].links[0].url': 'must be an http or https address',
          },
        })
      return json(200, {
        version: 2,
        profile: (body as { profile: Profile }).profile,
      })
    })
  }

  const projectsSection = () =>
    within(screen.getByRole('region', { name: 'Projects' }))

  it('adds a project with a name and type and sends empty lists', async () => {
    const calls = stubSaves(emptyProfile())
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      projectsSection().getByRole('button', { name: 'Add project' }),
    )
    await userEvent.type(
      projectsSection().getByLabelText('Name'),
      'Key-value store',
    )
    await userEvent.selectOptions(
      projectsSection().getByLabelText('Type'),
      'Work',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    expect(projects(calls)).toEqual([
      {
        name: 'Key-value store',
        type: 'WORK',
        context: null,
        start: null,
        end: null,
        description: null,
        technologies: [],
        results: [],
        links: [],
        highlight: false,
      },
    ])
  })

  it('shows a stored project with its type, link and highlight', async () => {
    stubSaves(full)
    mount()
    await screen.findByLabelText('City')
    expect(projectsSection().getByLabelText('Name')).toHaveValue(
      'Distributed key-value store',
    )
    expect(projectsSection().getByLabelText('Type')).toHaveValue('ASSIGNMENT')
    expect(projectsSection().getByLabelText('Address')).toHaveValue(
      'https://github.com/example/kv-store',
    )
    expect(projectsSection().getByLabelText('Highlight')).toBeChecked()
  })

  it('names an unsafe link on leaving it, and shows the server message when saved anyway', async () => {
    const calls = stubSaves(emptyProfile(), 400)
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      projectsSection().getByRole('button', { name: 'Add project' }),
    )
    await userEvent.type(projectsSection().getByLabelText('Name'), 'Tool')
    await userEvent.click(
      projectsSection().getByRole('button', { name: 'Add link' }),
    )
    await userEvent.type(projectsSection().getByLabelText('Label'), 'Code')
    await userEvent.type(
      projectsSection().getByLabelText('Address'),
      'javascript:alert(1)',
    )
    await userEvent.tab()
    expect(
      screen.getByText('Address: must be an http or https address'),
    ).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(1)
    const label = (
      await screen.findAllByText('Address: must be an http or https address')
    )[0]!.closest('label')!
    expect(within(label).getByRole('textbox')).toHaveValue(
      'javascript:alert(1)',
    )
  })

  it('saves the highlight of a project', async () => {
    const calls = stubSaves(emptyProfile())
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      projectsSection().getByRole('button', { name: 'Add project' }),
    )
    await userEvent.type(projectsSection().getByLabelText('Name'), 'Tool')
    await userEvent.click(projectsSection().getByLabelText('Highlight'))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    expect(projects(calls)[0]!.highlight).toBe(true)
  })
})

describe('Technology tags', () => {
  it('adds tags to a position, ignoring a repeat in another case', async () => {
    const calls = stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 1, profile: full })
        : json(200, {
            version: 2,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.type(
      screen.getAllByLabelText('Technologies')[0]!,
      'kotlin{Enter}Go{Enter}',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    const sent = calls.find((c) => c.method === 'PUT')!.body as {
      profile: Profile
    }
    expect(sent.profile.experience[0]!.technologies).toEqual([
      'Kotlin',
      'PostgreSQL',
      'Go',
    ])
  })
})
