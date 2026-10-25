import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/authContext.ts'
import { emptyProfile } from '../profile/types.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { LetterWorkspace } from './LetterWorkspace.tsx'
import type { SkillAnalysis } from './skillsApi.ts'

const SK = '/api/applications/app-1/skills'

const skill = (name: string) => ({
  name,
  level: null,
  origin: 'MANUAL' as const,
  sources: [],
})

const analysed = (note: string | null = null): SkillAnalysis => ({
  analysed: true,
  createdAt: '2026-10-25T10:00:00Z',
  include: [
    {
      name: 'Kotlin',
      importance: 'REQUIRED',
      profileIndex: 0,
      profileName: 'Kotlin',
    },
  ],
  gaps: [
    {
      name: 'Rust',
      importance: 'NICE_TO_HAVE',
      related: [{ name: 'C++', profileIndex: 2 }],
      note,
    },
  ],
  other: [{ name: 'Kafka', profileIndex: 1 }],
})

interface Call {
  method: string
  url: string
  body: unknown
}

function stub(opts: {
  initial?: SkillAnalysis
  analyse?: () => Response
  letters?: number
}) {
  let current: SkillAnalysis = opts.initial ?? { analysed: false }
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const body = init?.body ? JSON.parse(String(init.body)) : undefined
      calls.push({ method, url, body })
      if (url === '/api/profile')
        return new Response(
          JSON.stringify({
            version: 1,
            profile: {
              ...emptyProfile(),
              skills: [skill('Kotlin'), skill('Kafka'), skill('C++')],
            },
          }),
        )
      if (url === SK) return new Response(JSON.stringify(current))
      if (url === `${SK}/analyse`) {
        const res = opts.analyse?.() ?? new Response(JSON.stringify(analysed()))
        if (res.ok) current = (await res.clone().json()) as SkillAnalysis
        return res
      }
      if (url === `${SK}/notes`) {
        const first = (body as { notes: { text: string }[] }).notes[0]
        current = analysed(first?.text ?? null)
        return new Response(JSON.stringify(current))
      }
      if (url === '/api/applications/app-1/letters' && method === 'GET')
        return new Response('[]')
      if (url === '/api/applications/app-1/letters' && method === 'POST')
        return new Response(
          JSON.stringify({
            version: 1,
            how: 'GENERATED',
            language: 'EN',
            createdAt: '2026-10-25T10:00:00Z',
            basedOnVersion: null,
            text: 'Dear team',
            selection: { experience: [], projects: [], skills: [] },
            instructions: null,
            comments: null,
          }),
          { status: 201 },
        )
      return new Response('{}', { status: 404 })
    }),
  )
  return calls
}

const mount = () =>
  render(
    <AuthContext.Provider value={fakeAuth(signedIn())}>
      <LetterWorkspace applicationId="app-1" />
    </AuthContext.Provider>,
  )

afterEach(() => vi.unstubAllGlobals())

describe('the skills step', () => {
  it('offers the analysis and the old picker when there is none, and still generates', async () => {
    const calls = stub({})
    const user = userEvent.setup()
    mount()
    expect(
      await screen.findByRole('button', { name: 'Analyse the job' }),
    ).toBeInTheDocument()
    await user.click(await screen.findByRole('checkbox', { name: 'Kafka' }))
    await user.click(screen.getByRole('button', { name: 'Generate' }))
    await screen.findByText('Dear team')
    const post = calls.find((c) => c.method === 'POST')!
    expect((post.body as { skills: number[] }).skills).toEqual([1])
  })

  it('shows three lists with defaults and sends the ticked indexes, never note text', async () => {
    const calls = stub({ initial: analysed('I learn fast') })
    const user = userEvent.setup()
    mount()
    expect(await screen.findByText('Advised to include')).toBeInTheDocument()
    expect(screen.getByText('Gaps')).toBeInTheDocument()
    expect(
      screen.getByText(/Related skills of yours: C\+\+/),
    ).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Kotlin' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Kafka' })).not.toBeChecked()
    expect(screen.getByLabelText('Note on Rust')).toHaveValue('I learn fast')
    expect(screen.getByText('12 / 500')).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Kafka' }))
    await user.click(screen.getByRole('button', { name: 'Generate' }))
    await screen.findByText('Dear team')
    const post = calls.find(
      (c) => c.method === 'POST' && c.url.endsWith('/letters'),
    )!
    expect((post.body as { skills: number[] }).skills).toEqual([0, 1])
    expect(JSON.stringify(post.body)).not.toContain('I learn fast')
    expect(
      calls.some((c) => c.url === '/api/profile' && c.method !== 'GET'),
    ).toBe(false)
  })

  it('analyses on request', async () => {
    stub({})
    const user = userEvent.setup()
    mount()
    await user.click(
      await screen.findByRole('button', { name: 'Analyse the job' }),
    )
    expect(await screen.findByText('Your other skills')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Skills' })).toBeNull()
  })

  it('saves notes without a profile write and shows them after a reload', async () => {
    const calls = stub({ initial: analysed() })
    const user = userEvent.setup()
    const first = mount()
    await user.type(await screen.findByLabelText('Note on Rust'), 'Hello')
    await user.click(screen.getByRole('button', { name: 'Save notes' }))
    expect(
      await screen.findByText(/stay with this application/, {
        selector: '[role=status]',
      }),
    ).toBeInTheDocument()
    const put = calls.find((c) => c.method === 'PUT')!
    expect(put.body).toEqual({ notes: [{ skill: 'Rust', text: 'Hello' }] })
    expect(
      calls.some((c) => c.method === 'PUT' && c.url === '/api/profile'),
    ).toBe(false)
    first.unmount()
    mount()
    expect(await screen.findByLabelText('Note on Rust')).toHaveValue('Hello')
  })

  it.each([
    [503, 'The skill analysis is not available.'],
    [429, /limit of job analyses/],
    [502, /could not be analysed/],
  ])(
    'shows a fixed message for %i and keeps generating possible',
    async (status, text) => {
      stub({ analyse: () => new Response('{"error":"x"}', { status }) })
      const user = userEvent.setup()
      mount()
      await user.click(
        await screen.findByRole('button', { name: 'Analyse the job' }),
      )
      expect(await screen.findByText(text)).toBeInTheDocument()
      await user.click(screen.getByRole('checkbox', { name: 'Kotlin' }))
      expect(screen.getByRole('button', { name: 'Generate' })).toBeEnabled()
    },
  )
})
