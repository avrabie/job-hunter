import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/authContext.ts'
import { emptyProfile } from '../profile/types.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { CHECK_NOTICE, LetterWorkspace } from './LetterWorkspace.tsx'
import type { LetterDetail, LetterSummary } from './lettersApi.ts'

const BASE = '/api/applications/app-1/letters'

const profile = () => ({
  version: 1,
  profile: {
    ...emptyProfile(),
    experience: [
      {
        employer: 'Employer1 AG',
        title: 'Developer',
        start: '2020-01',
        end: null,
        location: null,
        achievements: [],
        technologies: [],
      },
      {
        employer: 'Employer2 GmbH',
        title: 'Engineer',
        start: '2018-01',
        end: '2019-12',
        location: null,
        achievements: [],
        technologies: [],
      },
    ],
    skills: [
      { name: 'Kotlin', level: null, origin: 'MANUAL', sources: [] },
      { name: 'Kafka', level: null, origin: 'MANUAL', sources: [] },
    ],
  },
})

const detail = (over: Partial<LetterDetail> = {}): LetterDetail => ({
  version: 1,
  how: 'GENERATED',
  language: 'EN',
  createdAt: '2026-10-24T12:00:00Z',
  basedOnVersion: null,
  text: 'Dear team,\n\nI built things at Employer1 AG.\n\nKind regards,\nAda Example',
  selection: { experience: [0], projects: [], skills: [] },
  instructions: null,
  comments: null,
  ...over,
})

const summary = (d: LetterDetail): LetterSummary => ({
  version: d.version,
  how: d.how,
  language: d.language,
  createdAt: d.createdAt,
  basedOnVersion: d.basedOnVersion,
})

interface Call {
  method: string
  url: string
  body: unknown
}

/** A small fake backend: `store` holds the versions; POST answers with `next` or an error. */
function stub(
  store: LetterDetail[],
  post: (body: Record<string, unknown>, isEdit: boolean) => Response = () =>
    new Response('{}', { status: 500 }),
) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const body = init?.body ? JSON.parse(String(init.body)) : undefined
      calls.push({ method, url, body })
      if (url === '/api/profile')
        return new Response(JSON.stringify(profile()), { status: 200 })
      if (url === BASE && method === 'GET')
        return new Response(JSON.stringify(store.map(summary)), { status: 200 })
      if (url === BASE && method === 'POST') {
        const res = post(body, false)
        if (res.ok) {
          const made = (await res.clone().json()) as LetterDetail
          store.push(made)
        }
        return res
      }
      if (url === `${BASE}/edited`) {
        const res = post(body, true)
        if (res.ok) store.push((await res.clone().json()) as LetterDetail)
        return res
      }
      const download = url.match(/\/letters\/(\d+)\/download$/)
      if (download) {
        const v = store.find((l) => l.version === Number(download[1]))!
        return new Response(v.text, {
          status: 200,
          headers: {
            'Content-Disposition': 'attachment; filename="motivation-x.md"',
          },
        })
      }
      const one = url.match(/\/letters\/(\d+)$/)
      if (one && method === 'GET') {
        const v = store.find((l) => l.version === Number(one[1]))
        return new Response(JSON.stringify(v), { status: v ? 200 : 404 })
      }
      if (one && method === 'DELETE') {
        const i = store.findIndex((l) => l.version === Number(one[1]))
        store.splice(i, 1)
        return new Response(null, { status: 204 })
      }
      return new Response('{}', { status: 404 })
    }),
  )
  return calls
}

const ok = (d: LetterDetail) => new Response(JSON.stringify(d), { status: 201 })
const fail = (status: number, error: string) =>
  new Response(JSON.stringify({ error }), { status })

function mount() {
  return render(
    <AuthContext.Provider value={fakeAuth(signedIn())}>
      <LetterWorkspace applicationId="app-1" />
    </AuthContext.Provider>,
  )
}

const posts = (calls: Call[]) =>
  calls.filter((c) => c.method === 'POST').map((c) => c.body as never)

let clicks: { download: string }[]

beforeEach(() => {
  clicks = []
  URL.createObjectURL = vi.fn(() => 'blob:letter-1')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicks.push({ download: this.download })
  })
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

async function generateFirst(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('checkbox', { name: /Developer/ }))
  await user.click(screen.getByRole('button', { name: 'Generate' }))
}

describe('the letter workspace', () => {
  it('needs at least one chosen entry before it can generate', async () => {
    stub([])
    const user = userEvent.setup()
    mount()
    const button = await screen.findByRole('button', { name: 'Generate' })
    expect(button).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: 'Kotlin' }))
    expect(button).toBeEnabled()
  })

  it('generates from the chosen indexes, language and instructions and shows numbered paragraphs', async () => {
    const calls = stub([], () => ok(detail({ language: 'DE' })))
    const user = userEvent.setup()
    mount()
    await user.selectOptions(await screen.findByLabelText('Language'), 'DE')
    await user.click(await screen.findByRole('checkbox', { name: /Engineer/ }))
    await user.click(screen.getByRole('checkbox', { name: 'Kafka' }))
    await user.type(screen.getByLabelText(/Instructions/), 'Mention teamwork')
    await user.click(screen.getByRole('button', { name: 'Generate' }))

    expect(await screen.findByText('Dear team,')).toBeInTheDocument()
    expect(posts(calls)).toEqual([
      {
        language: 'DE',
        experience: [1],
        projects: [],
        skills: [1],
        instructions: 'Mention teamwork',
        basedOnVersion: null,
        comments: null,
      },
    ])
    const items = screen
      .getAllByRole('listitem')
      .filter((li) => li.closest('.letter-paragraphs'))
    expect(items).toHaveLength(3)
    expect(screen.getByLabelText('Comment on paragraph 3')).toBeInTheDocument()
  })

  it('shows the checking notice and the current (newest) version of an application', async () => {
    stub([detail(), detail({ version: 2, text: 'Second draft.' })])
    mount()
    expect(await screen.findByText('Second draft.')).toBeInTheDocument()
    expect(screen.getByText(CHECK_NOTICE)).toBeInTheDocument()
    expect(CHECK_NOTICE).toBe(
      'Check every statement before you send this letter; the writing assistant can get facts wrong.',
    )
  })

  it('shows the letter as text, never as HTML', async () => {
    stub([
      detail({
        text: 'Hello <img src=x onerror=alert(1)> **bold**\n\n<b>x</b>',
      }),
    ])
    const { container } = mount()
    expect(
      await screen.findByText(
        /Hello <img src=x onerror=alert\(1\)> \*\*bold\*\*/,
      ),
    ).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(container.querySelector('strong')).toBeNull()
  })

  it('regenerates with the paragraph and general comments and keeps the old version listed', async () => {
    const calls = stub([detail()], () =>
      ok(
        detail({
          version: 2,
          how: 'REGENERATED',
          basedOnVersion: 1,
          text: 'Shorter letter.',
        }),
      ),
    )
    const user = userEvent.setup()
    mount()
    await user.type(
      await screen.findByLabelText('Comment on paragraph 2'),
      'shorter, mention Kafka',
    )
    await user.type(screen.getByLabelText('General comment'), 'More formal')
    await user.click(screen.getByRole('button', { name: 'Regenerate' }))

    expect(await screen.findByText('Shorter letter.')).toBeInTheDocument()
    expect(posts(calls)[0]).toMatchObject({
      basedOnVersion: 1,
      experience: [0],
      comments: {
        general: 'More formal',
        paragraphs: [{ n: 2, text: 'shorter, mention Kafka' }],
      },
    })
    const versions = screen.getByRole('region', { name: 'Versions' })
    expect(
      within(versions).getByRole('button', { name: 'Version 1' }),
    ).toBeInTheDocument()
    expect(
      within(versions).getByRole('button', { name: 'Version 2' }),
    ).toBeInTheDocument()
    // The comment boxes are empty for the new version.
    expect(screen.getByLabelText('General comment')).toHaveValue('')
  })

  it('reopens an older version from the list', async () => {
    stub([detail(), detail({ version: 2, text: 'Second draft.' })])
    const user = userEvent.setup()
    mount()
    await screen.findByText('Second draft.')
    await user.click(screen.getByRole('button', { name: 'Version 1' }))
    expect(await screen.findByText('Dear team,')).toBeInTheDocument()
    expect(screen.queryByText('Second draft.')).toBeNull()
  })

  it('saves an edited text as a new version', async () => {
    const calls = stub([detail()], (body) =>
      ok(
        detail({
          version: 2,
          how: 'EDITED',
          text: String(body.text),
        }),
      ),
    )
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const box = screen.getByLabelText('Letter text')
    await user.clear(box)
    await user.type(box, 'My own words.')
    await user.click(
      screen.getByRole('button', { name: 'Save as new version' }),
    )

    expect(await screen.findByText('My own words.')).toBeInTheDocument()
    expect(
      calls.filter((c) => c.method === 'POST').map((c) => [c.url, c.body]),
    ).toEqual([[`${BASE}/edited`, { text: 'My own words.' }]])
    expect(screen.getByText(/edited/)).toBeInTheDocument()
  })

  it('copies the current text to the clipboard', async () => {
    stub([detail({ text: 'Copy me.' })])
    const user = userEvent.setup()
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockResolvedValue()
    mount()
    await screen.findByText('Copy me.')
    await user.click(screen.getByRole('button', { name: 'Copy' }))
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('Copy me.'))
    expect(await screen.findByText('Copied to the clipboard.')).toBeVisible()
  })

  it('downloads the version as a Markdown file', async () => {
    const calls = stub([detail()])
    const user = userEvent.setup()
    mount()
    await user.click(
      await screen.findByRole('button', { name: 'Download .md' }),
    )
    await waitFor(() => expect(clicks).toHaveLength(1))
    expect(clicks[0]!.download).toBe('motivation-x.md')
    expect(calls.some((c) => c.url === `${BASE}/1/download`)).toBe(true)
  })

  it.each([
    [429, 'rate_limited', 'limit of letters for this hour'],
    [502, 'letter_failed', 'could not produce a usable letter'],
    [503, 'letter_not_configured', 'writing assistant is not available'],
    [409, 'letter_limit', 'already has 20 letter versions'],
    [400, 'selection_invalid', 'no longer match your saved profile'],
    [400, 'letter_text_invalid', 'longer than 6 000 characters'],
  ])('says the fixed message for %i %s', async (status, code, part) => {
    stub([], () => fail(status, code))
    const user = userEvent.setup()
    mount()
    await generateFirst(user)
    expect(await screen.findByRole('alert')).toHaveTextContent(part!)
    expect(screen.queryByRole('article')).toBeNull()
  })

  it('says the fixed message when a manual edit is refused', async () => {
    stub([detail()], () => fail(400, 'letter_text_invalid'))
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    await user.click(
      screen.getByRole('button', { name: 'Save as new version' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'longer than 6 000 characters',
    )
  })

  it('removes a version from the list', async () => {
    stub([detail(), detail({ version: 2, text: 'Second draft.' })])
    const user = userEvent.setup()
    mount()
    await screen.findByText('Second draft.')
    await user.click(screen.getByRole('button', { name: 'Remove version 1' }))
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Version 1' })).toBeNull(),
    )
  })
})
