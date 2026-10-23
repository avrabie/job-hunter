import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyDraft, extractProfile, extractSection } from './cvApi.ts'
import { emptyProfile } from '../profile/types.ts'

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}
const answer = (status: number, body: unknown = {}) =>
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), { status })),
  )

afterEach(() => vi.unstubAllGlobals())

describe('extractProfile', () => {
  it('returns the draft with every field present', async () => {
    answer(200, { skills: [{ name: 'Kotlin', level: null }] })
    const result = await extractProfile(auth)
    expect(result.kind).toBe('draft')
    if (result.kind === 'draft') {
      expect(result.profile.skills).toHaveLength(1)
      expect(result.profile.experience).toEqual([])
      expect(result.profile.preferences.targetRoles).toEqual([])
    }
  })
  it.each([
    [404, 'Upload a CV first.'],
    [409, 'Choose a master CV in your files first.'],
    [413, 'The CV text is too long to read.'],
    [
      422,
      'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
    ],
    [429, 'Too many extractions. Please try again later.'],
    [502, 'The CV could not be read right now. Please try again.'],
    [503, 'Reading CVs is not set up on this server yet.'],
  ])('maps %i to a fixed message', async (status, message) => {
    answer(status)
    expect(await extractProfile(auth)).toEqual({ kind: 'failed', message })
  })
})

describe('extractSection', () => {
  it('posts to the section query and returns the draft', async () => {
    const fetchMock = vi.fn(
      async (_url: string) =>
        new Response(
          JSON.stringify({
            education: [{ institution: 'Example University', degree: 'MSc' }],
          }),
          { status: 200 },
        ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const result = await extractSection(auth, 'education')
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/cv/extract?section=education',
    )
    expect(result.kind).toBe('draft')
    if (result.kind === 'draft') {
      expect(result.profile.education).toHaveLength(1)
      expect(result.profile.experience).toEqual([])
      expect(result.profile.preferences.targetRoles).toEqual([])
    }
  })
  it.each([
    [400, 'That section cannot be read.'],
    [404, 'Upload a CV first.'],
    [409, 'Choose a master CV in your files first.'],
    [413, 'The CV text is too long to read.'],
    [
      422,
      'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
    ],
    [429, 'Too many extractions. Please try again later.'],
    [502, 'The CV could not be read right now. Please try again.'],
    [503, 'Reading CVs is not set up on this server yet.'],
  ])('maps %i to a fixed message', async (status, message) => {
    answer(status)
    expect(await extractSection(auth, 'publications')).toEqual({
      kind: 'failed',
      message,
    })
  })
})

describe('applyDraft', () => {
  it('takes the CV facts and keeps the user preferences', () => {
    const current = emptyProfile()
    current.preferences.targetRoles = ['Backend']
    const draft = emptyProfile()
    draft.summary = 'From the CV'

    const result = applyDraft(current, draft)

    expect(result.summary).toBe('From the CV')
    expect(result.preferences.targetRoles).toEqual(['Backend'])
  })
})
