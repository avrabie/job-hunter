import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import fixture from './fixtures/full-profile.json'
import { ProfilePage } from './ProfilePage.tsx'
import {
  emptyProfile,
  type OpenSourceProject,
  type Profile,
  type ProjectAnalysis,
  type Skill,
  type SkillSource,
  type Technology,
} from './types.ts'

const full = fixture as unknown as Profile

type Handler = (method: string, body: unknown) => Response

/**
 * Stubs fetch for `/api/profile`; `calls` records the method and parsed body of each request. The file cabinet of the page
 * asks `/api/files` too: that is answered "no files" here and not recorded (its own tests are in `files/FileCabinet.test.tsx`).
 */
function stubApi(
  handler: Handler,
  publicCv: () => Promise<Response> | Response = () =>
    new Response('', { status: 404 }),
) {
  const calls: { method: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/public/postings?size=6')
        return new Response('{"items":[]}', { status: 200 })
      if (url === '/api/files') return new Response('[]', { status: 200 })
      if (url.startsWith('/api/files')) return new Response('', { status: 404 })
      // The public CV state of the notice: not recorded, the tests of the page look at the profile calls only.
      if (url === '/api/cv/public') return publicCv()
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
  function stubSections(
    master: boolean,
    extract: () => Response,
    stored: Profile = full,
  ) {
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
        if (method === 'GET') return json(200, { version: 1, profile: stored })
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

  it('disables the three buttons with a hint when there is no master CV', async () => {
    stubSections(false, () => json(200, emptyProfile()))
    mount()
    await screen.findByLabelText('City')
    for (const name of ['Contact', 'Experience', 'Education', 'Publications']) {
      expect(
        section(name).getByRole('button', { name: 'Fill from CV' }),
      ).toBeDisabled()
    }
    expect(
      screen.getAllByText('Choose a master CV in your files to use this.'),
    ).toHaveLength(4)
    expect(screen.queryByRole('region', { name: 'Projects' })).toBeNull()
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

  describe('Fill the contact section from the CV', () => {
    const cvContact = {
      fullName: 'Ada Example',
      headline: 'Engineer',
      email: 'ada@example.com',
      phone: '+41 79 000 00 00',
      website: 'https://example.com',
      linkedin: 'https://www.linkedin.com/in/ada-example',
    }
    const cvDraft = (contact: Partial<Profile['contact']>) =>
      json(200, {
        ...emptyProfile(),
        contact: { ...emptyProfile().contact, ...contact },
      })
    const emptyStored = (): Profile => ({
      ...full,
      contact: emptyProfile().contact,
    })
    const contactSection = () => section('Contact')
    const requests = (calls: { url: string; method: string }[]) =>
      calls.filter((c) => c.url.startsWith('/api/cv/extract'))

    it('fills the empty fields, unsaved, and sends no profile request', async () => {
      const calls = stubSections(true, () => cvDraft(cvContact), emptyStored())
      mount()
      await screen.findByLabelText('City')
      const button = contactSection().getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      expect(
        await screen.findByText(/Nothing is saved until you press Save/),
      ).toBeVisible()
      expect(contactSection().getByLabelText('Full name')).toHaveValue(
        'Ada Example',
      )
      expect(contactSection().getByLabelText('Email')).toHaveValue(
        'ada@example.com',
      )
      expect(contactSection().getByLabelText('Phone')).toHaveValue(
        '+41 79 000 00 00',
      )
      expect(contactSection().getByLabelText('Website')).toHaveValue(
        'https://example.com',
      )
      expect(contactSection().getByLabelText('LinkedIn')).toHaveValue(
        'https://www.linkedin.com/in/ada-example',
      )
      expect(requests(calls)).toEqual([
        { url: '/api/cv/extract?section=contact', method: 'POST' },
      ])
      expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
    })

    it('asks before replacing a different value, naming the field and not its value', async () => {
      stubSections(true, () => cvDraft(cvContact), {
        ...emptyStored(),
        contact: { ...emptyProfile().contact, fullName: 'Grace Sample' },
      })
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
      mount()
      await screen.findByLabelText('City')
      const button = contactSection().getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      await waitFor(() =>
        expect(contactSection().getByLabelText('Full name')).toHaveValue(
          'Ada Example',
        ),
      )
      expect(confirm).toHaveBeenCalledTimes(1)
      const message = confirm.mock.calls[0]![0] as string
      expect(message).toContain('Full name')
      expect(message).not.toContain('Grace Sample')
      expect(message).not.toContain('Ada Example')
      expect(contactSection().getByLabelText('Email')).toHaveValue(
        'ada@example.com',
      )
    })

    it('keeps a different value when the user declines, and still fills the empty fields', async () => {
      stubSections(true, () => cvDraft(cvContact), {
        ...emptyStored(),
        contact: { ...emptyProfile().contact, fullName: 'Grace Sample' },
      })
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
      mount()
      await screen.findByLabelText('City')
      const button = contactSection().getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      expect(
        await screen.findByText(/Nothing is saved until you press Save/),
      ).toBeVisible()
      expect(confirm).toHaveBeenCalledTimes(1)
      expect(contactSection().getByLabelText('Full name')).toHaveValue(
        'Grace Sample',
      )
      expect(contactSection().getByLabelText('Email')).toHaveValue(
        'ada@example.com',
      )
    })

    it('says nothing was found and changes nothing when the CV has no contact', async () => {
      const calls = stubSections(true, () => cvDraft({}), emptyStored())
      mount()
      await screen.findByLabelText('City')
      const button = contactSection().getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      expect(
        await screen.findByText(
          'No contact details were found in your CV. The contact fields are unchanged.',
        ),
      ).toBeVisible()
      expect(contactSection().getByLabelText('Full name')).toHaveValue('')
      expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
    })

    it('shows the fixed message in the Contact section and keeps the fields', async () => {
      stubSections(true, () => json(429, {}), emptyStored())
      mount()
      await screen.findByLabelText('City')
      const button = contactSection().getByRole('button', {
        name: 'Fill from CV',
      })
      await waitFor(() => expect(button).toBeEnabled())
      await userEvent.click(button)

      expect(await contactSection().findByRole('alert')).toHaveTextContent(
        'Too many extractions. Please try again later.',
      )
      expect(contactSection().getByLabelText('Full name')).toHaveValue('')
    })
  })
})

describe('Open Source Projects section', () => {
  const oss = () =>
    within(screen.getByRole('region', { name: 'Open Source Projects' }))

  const sentProfile = (calls: { method: string; body: unknown }[]) =>
    (calls.find((c) => c.method === 'PUT')!.body as { profile: Profile })
      .profile

  /**
   * Answers the profile, the repository listing (`listing`) and the save (`save`). `calls` records every request of the
   * profile and the listing, with the URL.
   */
  function stubOss(
    stored: Profile,
    options: { listing?: () => Response; save?: () => Response } = {},
  ) {
    const calls: { url: string; method: string; body: unknown }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/files') return json(200, [])
        if (url.startsWith('/api/files'))
          return new Response('', { status: 404 })
        const method = init?.method ?? 'GET'
        const body = init?.body ? JSON.parse(init.body as string) : undefined
        calls.push({ url, method, body })
        if (url.startsWith('/api/profile/repositories')) {
          return options.listing?.() ?? json(200, { repositories: [] })
        }
        if (method === 'GET') return json(200, { version: 1, profile: stored })
        if (options.save) return options.save()
        return json(200, {
          version: 2,
          profile: (body as { profile: Profile }).profile,
        })
      }),
    )
    return calls
  }

  it('adds an account and saves it', async () => {
    const calls = stubOss(emptyProfile())
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Add code account' }),
    )
    await userEvent.type(oss().getByLabelText('Username'), 'octocat')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    expect(sentProfile(calls).codeAccounts).toEqual([
      { provider: 'GITHUB', username: 'octocat' },
    ])
  })

  it('enables Load repositories only for a valid username', async () => {
    stubOss(emptyProfile())
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Add code account' }),
    )
    const load = oss().getByRole('button', { name: 'Load repositories' })
    expect(load).toBeDisabled()
    await userEvent.type(oss().getByLabelText('Username'), '-bad-')
    expect(load).toBeDisabled()
    await userEvent.clear(oss().getByLabelText('Username'))
    await userEvent.type(oss().getByLabelText('Username'), 'octocat')
    expect(load).toBeEnabled()
  })

  it('names a bad username on leaving it, and shows the server message when saved anyway', async () => {
    const message = 'Username: must follow the username rule of the provider'
    const calls = stubOss(emptyProfile(), {
      save: () =>
        json(400, {
          error: 'validation_failed',
          fields: {
            'codeAccounts[0].username':
              'must follow the username rule of the provider',
          },
        }),
    })
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Add code account' }),
    )
    await userEvent.type(oss().getByLabelText('Username'), '-bad-')
    await userEvent.tab()
    expect(screen.getByText(message)).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(1)
    const label = (await screen.findAllByText(message))[0]!.closest('label')!
    expect(within(label).getByRole('textbox')).toHaveValue('-bad-')
  })

  it('keeps the note and the new order of a picked project', async () => {
    const [first] = full.openSourceProjects
    const stored: Profile = {
      ...full,
      openSourceProjects: [
        first!,
        { ...first!, fullName: 'octocat/spoon-knife', name: 'spoon-knife' },
      ],
    }
    const calls = stubOss(stored)
    mount()
    await screen.findByLabelText('City')
    await userEvent.type(oss().getAllByLabelText('Note')[1]!, ' again')
    await userEvent.click(
      oss().getByRole('button', { name: 'Move Project 2 up' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    const projects = sentProfile(calls).openSourceProjects
    expect(projects.map((p) => p.name)).toEqual(['spoon-knife', 'hello-world'])
    expect(projects[0]!.note).toBe('Maintainer since 2022 again')
    expect(projects[1]!.note).toBe('Maintainer since 2022')
  })

  it('shows a stored project with its facts and a link to the repository', async () => {
    stubOss(full)
    mount()
    await screen.findByLabelText('City')
    expect(oss().getByRole('link', { name: 'hello-world' })).toHaveAttribute(
      'href',
      'https://github.com/octocat/hello-world',
    )
    expect(oss().getByText('My first repository')).toBeVisible()
    expect(oss().getByLabelText('Note')).toHaveValue('Maintainer since 2022')
  })

  it('removes a picked project and saves the rest', async () => {
    const calls = stubOss(full)
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Remove Project 1' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 2).')).toBeVisible()
    expect(sentProfile(calls).openSourceProjects).toEqual([])
  })
})

describe('Pick repositories from a code account', () => {
  const oss = () =>
    within(screen.getByRole('region', { name: 'Open Source Projects' }))

  const REPOS = [
    {
      provider: 'GITHUB',
      fullName: 'octocat/hello-world',
      name: 'hello-world',
      url: 'https://github.com/octocat/hello-world',
      description: 'My first repository',
      language: 'Kotlin',
      topics: ['demo', 'kotlin'],
      stars: 42,
      pushedAt: '2026-09-30T12:00:00Z',
    },
    {
      provider: 'GITHUB',
      fullName: 'octocat/spoon-knife',
      name: 'spoon-knife',
      url: 'https://github.com/octocat/spoon-knife',
      description: null,
      language: null,
      topics: [],
      stars: 0,
      pushedAt: null,
    },
  ]

  /** The profile answers with `stored`, the listing with `listing`; `calls` records the URL and method of each. */
  function stubListing(stored: Profile, listing: () => Response) {
    const calls: { url: string; method: string }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/files') return json(200, [])
        if (url.startsWith('/api/files'))
          return new Response('', { status: 404 })
        const method = init?.method ?? 'GET'
        calls.push({ url, method })
        if (url.startsWith('/api/profile/repositories')) return listing()
        if (method === 'GET') return json(200, { version: 1, profile: stored })
        return json(200, { version: 2, profile: emptyProfile() })
      }),
    )
    return calls
  }

  /** Adds an account with the username and loads its repositories. */
  async function loadAccount(username = 'octocat') {
    await userEvent.click(
      oss().getByRole('button', { name: 'Add code account' }),
    )
    await userEvent.type(oss().getByLabelText('Username'), username)
    await userEvent.click(
      oss().getByRole('button', { name: 'Load repositories' }),
    )
  }

  it('loads the repositories and picks two, unsaved, with no profile request', async () => {
    const calls = stubListing(emptyProfile(), () =>
      json(200, { repositories: REPOS }),
    )
    mount()
    await screen.findByLabelText('City')
    await loadAccount()
    await userEvent.click(
      await oss().findByRole('checkbox', { name: /hello-world/ }),
    )
    await userEvent.click(oss().getByRole('checkbox', { name: /spoon-knife/ }))

    expect(oss().getByRole('link', { name: 'hello-world' })).toBeVisible()
    expect(oss().getByRole('link', { name: 'spoon-knife' })).toBeVisible()
    expect(
      screen.getByText('Nothing is saved until you press Save.'),
    ).toBeVisible()
    expect(calls).toContainEqual({
      url: '/api/profile/repositories?provider=GITHUB&username=octocat',
      method: 'GET',
    })
    expect(
      calls.filter((c) => c.url === '/api/profile' && c.method === 'PUT'),
    ).toHaveLength(0)
  })

  it('shows a picked repository ticked, and unticking it removes it', async () => {
    stubListing(full, () => json(200, { repositories: REPOS }))
    mount()
    await screen.findByLabelText('City')
    await loadAccount()
    const box = await oss().findByRole('checkbox', { name: /hello-world/ })
    expect(box).toBeChecked()
    await userEvent.click(box)

    expect(box).not.toBeChecked()
    expect(oss().queryByRole('link', { name: 'hello-world' })).toBeNull()
  })

  it('keeps a picked repository that is missing from the list', async () => {
    stubListing(full, () => json(200, { repositories: [REPOS[1]] }))
    mount()
    await screen.findByLabelText('City')
    await loadAccount()
    await oss().findByRole('checkbox', { name: /spoon-knife/ })

    expect(oss().getByRole('link', { name: 'hello-world' })).toBeVisible()
  })

  it('shows the fixed message for an unknown account and keeps the picks', async () => {
    stubListing(full, () => json(404, { error: 'account_not_found' }))
    mount()
    await screen.findByLabelText('City')
    await loadAccount('nobody-here')

    expect(await oss().findByRole('alert')).toHaveTextContent(
      'No account with that username was found.',
    )
    expect(oss().getByRole('link', { name: 'hello-world' })).toBeVisible()
  })

  it('asks to try again later when the provider is rate limited', async () => {
    stubListing(full, () => json(429, { error: 'provider_rate_limited' }))
    mount()
    await screen.findByLabelText('City')
    await loadAccount()

    expect(await oss().findByRole('alert')).toHaveTextContent(
      'Please try again later.',
    )
    expect(oss().getByRole('link', { name: 'hello-world' })).toBeVisible()
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

describe('Analyze a picked project', () => {
  const oss = () =>
    within(screen.getByRole('region', { name: 'Open Source Projects' }))
  // The Skills heading now sits in the summary line of a collapsible section, so the section is found as a region.
  const skillsSection = () =>
    within(screen.getByRole('region', { name: 'Skills' }))
  const ANALYZE = '/api/profile/open-source-projects/analyze'

  const PROJECT: OpenSourceProject = {
    provider: 'GITHUB',
    fullName: 'octocat/hello-world',
    name: 'hello-world',
    url: 'https://github.com/octocat/hello-world',
    description: 'My first repository',
    language: 'Kotlin',
    topics: ['demo'],
    stars: 42,
    note: null,
    analysis: null,
  }
  const REPO = {
    provider: 'GITHUB',
    fullName: 'octocat/hello-world',
    name: 'hello-world',
    url: 'https://github.com/octocat/hello-world',
    description: 'My first repository',
    language: 'Kotlin',
    topics: ['demo'],
    stars: 42,
    pushedAt: null,
  }
  const KOTLIN: Skill = {
    name: 'Kotlin',
    level: 'EXPERT',
    origin: 'MANUAL',
    sources: [],
  }
  const SOURCE: SkillSource = {
    provider: 'GITHUB',
    fullName: 'octocat/hello-world',
    url: 'https://github.com/octocat/hello-world',
    evidence:
      'build.gradle.kts: spring-boot gradle plugin and starter dependencies',
  }
  const SPRING_BOOT: Skill = {
    name: 'Spring Boot',
    level: null,
    origin: 'ANALYSIS',
    sources: [SOURCE],
  }
  const analysis = (techStack: Technology[]): ProjectAnalysis => ({
    summary: 'A small service.',
    architecture: 'One Gradle module.',
    techStack,
    files: ['README.md', 'build.gradle.kts'],
    truncated: false,
    analyzedAt: '2026-10-24T12:00:00Z',
  })
  const SPRING = analysis([
    {
      name: 'Spring Boot',
      category: 'FRAMEWORK',
      evidencePath: 'build.gradle.kts',
      evidence: 'spring-boot gradle plugin and starter dependencies',
    },
  ])
  const KOTLIN_ANALYSIS = analysis([
    {
      name: 'kotlin',
      category: 'LANGUAGE',
      evidencePath: 'build.gradle.kts',
      evidence: 'kotlin jvm plugin',
    },
  ])

  /** The profile answers with `stored`; the analysis answers with `analyze`. `calls` records each request. */
  function stubAnalysis(
    stored: Profile,
    options: { analyze?: () => Response; listing?: () => Response } = {},
  ) {
    const calls: { url: string; method: string; body: unknown }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/files') return json(200, [])
        if (url.startsWith('/api/files'))
          return new Response('', { status: 404 })
        const method = init?.method ?? 'GET'
        const body = init?.body ? JSON.parse(init.body as string) : undefined
        calls.push({ url, method, body })
        if (url === ANALYZE) return options.analyze?.() ?? json(200, {})
        if (url.startsWith('/api/profile/repositories'))
          return options.listing?.() ?? json(200, { repositories: [] })
        if (method === 'GET') return json(200, { version: 1, profile: stored })
        return json(200, {
          version: 2,
          profile: (body as { profile: Profile }).profile,
        })
      }),
    )
    return calls
  }

  const storedWith = (over: Partial<Profile>): Profile => ({
    ...emptyProfile(),
    openSourceProjects: [PROJECT],
    skills: [KOTLIN],
    ...over,
  })

  const analyzeButton = () =>
    oss().getByRole('button', { name: 'Analyze Project 1' })

  it('analyzes a saved project: shows the analysis, adds the skill, and does not save', async () => {
    const calls = stubAnalysis(storedWith({}), {
      analyze: () => json(200, { analysis: SPRING }),
    })
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(analyzeButton())

    expect(await oss().findByText('A small service.')).toBeVisible()
    expect(oss().getByText('One Gradle module.')).toBeVisible()
    expect(oss().getByText('Spring Boot')).toBeVisible()
    expect(oss().getByText('FRAMEWORK')).toBeVisible()
    expect(
      screen.getByText(/hello-world was analyzed\. .* Nothing is saved/),
    ).toBeVisible()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
    expect(calls.find((c) => c.url === ANALYZE)!.body).toEqual({
      provider: 'GITHUB',
      fullName: 'octocat/hello-world',
    })
  })

  it('disables Analyze for a project that is picked but not saved', async () => {
    stubAnalysis(emptyProfile(), {
      listing: () => json(200, { repositories: [REPO] }),
    })
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Add code account' }),
    )
    await userEvent.type(oss().getByLabelText('Username'), 'octocat')
    await userEvent.click(
      oss().getByRole('button', { name: 'Load repositories' }),
    )
    await userEvent.click(
      await oss().findByRole('checkbox', { name: /hello-world/ }),
    )

    expect(analyzeButton()).toBeDisabled()
    expect(
      oss().getByText('Save this project first to analyze it.'),
    ).toBeVisible()
  })

  it('shows the fixed message on a failure and changes nothing', async () => {
    const calls = stubAnalysis(storedWith({}), {
      analyze: () => json(429, { error: 'too_many_analyses' }),
    })
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(analyzeButton())

    expect(await oss().findByRole('alert')).toHaveTextContent(
      'You have reached the limit of analyses for this hour.',
    )
    expect(oss().queryByText('A small service.')).toBeNull()
    expect(skillsSection().getAllByLabelText('Skill name')).toHaveLength(1)
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('asks before replacing an existing analysis, and declining sends nothing', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const calls = stubAnalysis(
      storedWith({
        openSourceProjects: [{ ...PROJECT, analysis: SPRING }],
        skills: [SPRING_BOOT],
      }),
      { analyze: () => json(200, { analysis: SPRING }) },
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(analyzeButton())

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(calls.some((c) => c.url === ANALYZE)).toBe(false)
  })

  it('replaces the analysis when the user confirms the re-analysis', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = stubAnalysis(
      storedWith({
        openSourceProjects: [{ ...PROJECT, analysis: SPRING }],
        skills: [SPRING_BOOT],
      }),
      { analyze: () => json(200, { analysis: KOTLIN_ANALYSIS }) },
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(analyzeButton())

    expect(await oss().findByText('kotlin')).toBeVisible()
    expect(calls.some((c) => c.url === ANALYZE)).toBe(true)
  })
})

describe('Traceable skills from analysis', () => {
  const oss = () =>
    within(screen.getByRole('region', { name: 'Open Source Projects' }))
  // The Skills heading now sits in the summary line of a collapsible section, so the section is found as a region.
  const skillsSection = () =>
    within(screen.getByRole('region', { name: 'Skills' }))
  const ANALYZE = '/api/profile/open-source-projects/analyze'

  const PROJECT: OpenSourceProject = {
    provider: 'GITHUB',
    fullName: 'octocat/hello-world',
    name: 'hello-world',
    url: 'https://github.com/octocat/hello-world',
    description: null,
    language: 'Kotlin',
    topics: [],
    stars: 42,
    note: null,
    analysis: null,
  }
  const SOURCE: SkillSource = {
    provider: 'GITHUB',
    fullName: 'octocat/hello-world',
    url: 'https://github.com/octocat/hello-world',
    evidence:
      'build.gradle.kts: spring-boot gradle plugin and starter dependencies',
  }
  const SPRING_BOOT: Skill = {
    name: 'Spring Boot',
    level: null,
    origin: 'ANALYSIS',
    sources: [SOURCE],
  }
  const KOTLIN: Skill = {
    name: 'Kotlin',
    level: 'EXPERT',
    origin: 'MANUAL',
    sources: [],
  }
  const analysis = (name: string): ProjectAnalysis => ({
    summary: 'A small service.',
    architecture: 'One Gradle module.',
    techStack: [
      {
        name,
        category: 'FRAMEWORK',
        evidencePath: 'build.gradle.kts',
        evidence: 'spring-boot gradle plugin and starter dependencies',
      },
    ],
    files: ['README.md', 'build.gradle.kts'],
    truncated: false,
    analyzedAt: '2026-10-24T12:00:00Z',
  })

  function stubAnalysis(stored: Profile, analyze?: () => Response) {
    const calls: { url: string; method: string; body: unknown }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/files') return json(200, [])
        if (url.startsWith('/api/files'))
          return new Response('', { status: 404 })
        const method = init?.method ?? 'GET'
        const body = init?.body ? JSON.parse(init.body as string) : undefined
        calls.push({ url, method, body })
        if (url === ANALYZE) return analyze?.() ?? json(200, {})
        if (method === 'GET') return json(200, { version: 1, profile: stored })
        return json(200, {
          version: 2,
          profile: (body as { profile: Profile }).profile,
        })
      }),
    )
    return calls
  }

  const storedWith = (over: Partial<Profile>): Profile => ({
    ...emptyProfile(),
    openSourceProjects: [PROJECT],
    ...over,
  })

  const analyzeFirst = () =>
    userEvent.click(oss().getByRole('button', { name: 'Analyze Project 1' }))

  it('adds a new skill with origin analysis, no level and the repository as its source', async () => {
    stubAnalysis(storedWith({ skills: [KOTLIN] }), () =>
      json(200, { analysis: analysis('Spring Boot') }),
    )
    mount()
    await screen.findByLabelText('City')
    await analyzeFirst()

    const names = skillsSection().getAllByLabelText('Skill name')
    expect(names).toHaveLength(2)
    expect(names[1]).toHaveValue('Spring Boot')
    expect(skillsSection().getAllByLabelText('Skill level')[1]).toHaveValue('')
    expect(skillsSection().getByText('from repository analysis')).toBeVisible()
    const link = skillsSection().getByRole('link', {
      name: 'octocat/hello-world',
    })
    expect(link).toHaveAttribute(
      'href',
      'https://github.com/octocat/hello-world',
    )
    expect(
      skillsSection().getByText(
        'build.gradle.kts: spring-boot gradle plugin and starter dependencies',
      ),
    ).toBeVisible()
  })

  it('gives an existing skill the source and keeps its level and origin', async () => {
    stubAnalysis(storedWith({ skills: [KOTLIN] }), () =>
      json(200, { analysis: analysis('kotlin') }),
    )
    mount()
    await screen.findByLabelText('City')
    await analyzeFirst()

    const names = skillsSection().getAllByLabelText('Skill name')
    expect(names).toHaveLength(1)
    expect(skillsSection().getByLabelText('Skill level')).toHaveValue('EXPERT')
    expect(skillsSection().queryByText('from repository analysis')).toBeNull()
    expect(
      skillsSection().getByRole('link', { name: 'octocat/hello-world' }),
    ).toBeVisible()
  })

  it('replaces the skills of an earlier analysis of the same repository', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    stubAnalysis(
      storedWith({
        openSourceProjects: [{ ...PROJECT, analysis: analysis('Spring Boot') }],
        skills: [KOTLIN, SPRING_BOOT],
      }),
      () => json(200, { analysis: analysis('Kotlin') }),
    )
    mount()
    await screen.findByLabelText('City')
    await analyzeFirst()

    const names = skillsSection()
      .getAllByLabelText('Skill name')
      .map((el) => (el as HTMLInputElement).value)
    expect(names).toEqual(['Kotlin'])
  })

  it('removes the skills of a project when the project is removed', async () => {
    stubAnalysis(
      storedWith({
        openSourceProjects: [{ ...PROJECT, analysis: analysis('Spring Boot') }],
        skills: [KOTLIN, SPRING_BOOT],
      }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Remove Project 1' }),
    )

    expect(skillsSection().getAllByLabelText('Skill name')).toHaveLength(1)
    expect(skillsSection().getByLabelText('Skill name')).toHaveValue('Kotlin')
  })

  it('removes the analysis skills of a project when its analysis is removed', async () => {
    stubAnalysis(
      storedWith({
        openSourceProjects: [{ ...PROJECT, analysis: analysis('Spring Boot') }],
        skills: [KOTLIN, SPRING_BOOT],
      }),
    )
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      oss().getByRole('button', { name: 'Remove analysis of Project 1' }),
    )

    expect(skillsSection().getAllByLabelText('Skill name')).toHaveLength(1)
    expect(oss().queryByText('A small service.')).toBeNull()
  })

  it('lets the user remove a source, and a skill, with their own buttons', async () => {
    stubAnalysis(storedWith({ skills: [KOTLIN, SPRING_BOOT] }))
    mount()
    await screen.findByLabelText('City')
    await userEvent.click(
      skillsSection().getByRole('button', {
        name: 'Remove source 1 of Skill 2',
      }),
    )
    expect(
      skillsSection().queryByRole('link', { name: 'octocat/hello-world' }),
    ).toBeNull()

    await userEvent.click(
      skillsSection().getByRole('button', { name: 'Remove Skill 2' }),
    )
    expect(skillsSection().getAllByLabelText('Skill name')).toHaveLength(1)
  })

  it('saves the analysis on the project and the origin and sources of the skills', async () => {
    const calls = stubAnalysis(storedWith({ skills: [KOTLIN] }), () =>
      json(200, { analysis: analysis('Spring Boot') }),
    )
    mount()
    await screen.findByLabelText('City')
    await analyzeFirst()
    await oss().findByText('A small service.')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    const put = calls.find((c) => c.method === 'PUT')!
    const sent = (put.body as { profile: Profile }).profile
    expect(sent.openSourceProjects[0]!.analysis).toEqual(
      analysis('Spring Boot'),
    )
    expect(sent.skills[1]).toEqual({
      name: 'Spring Boot',
      level: null,
      origin: 'ANALYSIS',
      sources: [
        {
          provider: 'GITHUB',
          fullName: 'octocat/hello-world',
          url: 'https://github.com/octocat/hello-world',
          evidence:
            'build.gradle.kts: spring-boot gradle plugin and starter dependencies',
        },
      ],
    })
  })
})

describe('Contact section', () => {
  it('fills the contact fields, saves them, and shows the same values after a reload', async () => {
    const calls = stubApi((method, body) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(200, {
            version: 1,
            profile: (body as { profile: Profile }).profile,
          }),
    )
    const first = mount()
    await screen.findByLabelText('Full name')
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada Example')
    await userEvent.type(screen.getByLabelText('Headline'), 'Engineer')
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.org')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved (version 1).')).toBeVisible()
    const sent = calls.find((c) => c.method === 'PUT')!.body as {
      profile: Profile
    }
    expect(sent.profile.contact).toEqual({
      fullName: 'Ada Example',
      headline: 'Engineer',
      email: 'ada@example.org',
      phone: null,
      website: null,
      linkedin: null,
    })
    first.unmount()

    stubApi(() => json(200, { version: 1, profile: sent.profile }))
    mount()
    expect(await screen.findByDisplayValue('Ada Example')).toBeVisible()
    expect(screen.getByDisplayValue('ada@example.org')).toBeVisible()
  })

  it('names a bad email on leaving it, without saving', async () => {
    const calls = stubApi(() =>
      json(200, { version: 0, profile: emptyProfile() }),
    )
    mount()
    await screen.findByLabelText('Email')
    await userEvent.type(screen.getByLabelText('Email'), 'not an email')
    await userEvent.tab()
    expect(screen.getByText('Email: must be an email address')).toBeVisible()
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0)
  })

  it('shows the server message at the email field when saved anyway', async () => {
    const calls = stubApi((method) =>
      method === 'GET'
        ? json(200, { version: 0, profile: emptyProfile() })
        : json(400, {
            error: 'validation_failed',
            fields: { 'contact.email': 'must be an email address' },
          }),
    )
    mount()
    await screen.findByLabelText('Email')
    await userEvent.type(screen.getByLabelText('Email'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(1)
    const label = (
      await screen.findAllByText('Email: must be an email address')
    )[0]!.closest('label')!
    expect(within(label).getByRole('textbox')).toHaveValue('nope')
  })

  it('says the full name is needed to export and links to the export page', async () => {
    stubApi(() => json(200, { version: 0, profile: emptyProfile() }))
    mount()
    expect(
      await screen.findByText('The full name is needed to export a CV.'),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Export CV' })).toHaveAttribute(
      'href',
      '/export',
    )
  })
})

describe('Public CV notice', () => {
  const live = () =>
    json(200, {
      configured: true,
      handle: 'ada-example',
      published: true,
      locked: false,
      settings: {},
      handleChangeableAt: null,
      updatedAt: '2026-10-24T10:00:00Z',
    })

  it('shows the live address with a link to the settings', async () => {
    stubApi(
      () => json(200, { version: 0, profile: emptyProfile() }),
      () => live(),
    )
    mount()
    expect(
      await screen.findByText(
        `Your public CV is live at ${window.location.origin}/cv/ada-example; saved changes show there at once.`,
      ),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Public CV' })).toHaveAttribute(
      'href',
      '/publish',
    )
    expect(screen.getByLabelText('Full name')).toBeVisible()
  })

  it('shows only the Public CV link when there is no public page', async () => {
    stubApi(
      () => json(200, { version: 0, profile: emptyProfile() }),
      () => json(200, { configured: false }),
    )
    mount()
    expect(
      await screen.findByRole('link', { name: 'Public CV' }),
    ).toHaveAttribute('href', '/publish')
    expect(screen.queryByText(/is live at/)).toBeNull()
  })

  it('works as before and shows no notice when the call fails', async () => {
    stubApi(
      () => json(200, { version: 0, profile: emptyProfile() }),
      () => {
        throw new TypeError('network down')
      },
    )
    mount()
    expect(await screen.findByLabelText('Full name')).toBeVisible()
    expect(screen.queryByText(/is live at/)).toBeNull()
    expect(screen.queryByRole('link', { name: 'Public CV' })).toBeNull()
  })

  it('does not wait for a slow call', async () => {
    stubApi(
      () => json(200, { version: 0, profile: emptyProfile() }),
      () => new Promise<Response>(() => {}),
    )
    mount()
    expect(await screen.findByLabelText('Full name')).toBeVisible()
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada Example')
    expect(screen.getByLabelText('Full name')).toHaveValue('Ada Example')
    expect(screen.queryByText(/is live at/)).toBeNull()
    expect(screen.queryByRole('link', { name: 'Public CV' })).toBeNull()
  })
})
