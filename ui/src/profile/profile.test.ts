import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import fixture from './fixtures/full-profile.json'
import { loadProfile, saveProfile } from './profileApi.ts'
import { emptyProfile, toRequestProfile, type Profile } from './types.ts'

// The JSON import has plain strings where the types have unions; the shape is what is checked here.
const full = fixture as unknown as Profile

afterEach(() => vi.unstubAllGlobals())

function stubFetch(status: number, body: unknown = {}) {
  const spy = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(JSON.stringify(body), { status }),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

describe('types match the backend sample', () => {
  it('has the same keys at every level as an empty profile plus its entries', () => {
    expect(Object.keys(full).sort()).toEqual(Object.keys(emptyProfile()).sort())
    expect(Object.keys(full.preferences).sort()).toEqual(
      Object.keys(emptyProfile().preferences).sort(),
    )
    expect(Object.keys(full.experience[0]!).sort()).toEqual([
      'achievements',
      'employer',
      'end',
      'location',
      'start',
      'title',
    ])
    expect(Object.keys(full.education[0]!).sort()).toEqual([
      'degree',
      'end',
      'field',
      'institution',
      'start',
    ])
    expect(Object.keys(full.publications[0]!).sort()).toEqual([
      'authors',
      'title',
      'url',
      'venue',
      'year',
    ])
  })

  it('trims a publication and sends empty optional fields as null', () => {
    const p: Profile = {
      ...emptyProfile(),
      publications: [
        {
          title: ' Paper ',
          venue: '  ',
          year: 2020,
          authors: '',
          url: ' https://example.org/p ',
        },
      ],
    }
    expect(toRequestProfile(p).publications[0]).toEqual({
      title: 'Paper',
      venue: null,
      year: 2020,
      authors: null,
      url: 'https://example.org/p',
    })
  })

  it('goes to the wire unchanged when it is already clean', () => {
    expect(toRequestProfile(full)).toEqual(full)
  })
})

describe('toRequestProfile', () => {
  it('turns empty optional text into null, trims, and drops an empty location', () => {
    const p: Profile = {
      ...emptyProfile(),
      location: { city: '  ', canton: '' },
      summary: '   ',
      experience: [
        {
          employer: ' E ',
          title: ' T ',
          start: '2020-01',
          end: '',
          location: ' ',
          achievements: [' did x '],
        },
      ],
      education: [
        { institution: 'I', degree: 'D', field: '', start: '', end: '' },
      ],
    }
    const out = toRequestProfile(p)
    expect(out.location).toBeNull()
    expect(out.summary).toBeNull()
    expect(out.experience[0]).toEqual({
      employer: 'E',
      title: 'T',
      start: '2020-01',
      end: null,
      location: null,
      achievements: ['did x'],
    })
    expect(out.education[0]).toMatchObject({
      field: null,
      start: null,
      end: null,
    })
  })
})

describe('loadProfile', () => {
  it('returns the stored profile and sends the token', async () => {
    const spy = stubFetch(200, { version: 3, profile: full })
    const auth = fakeAuth(signedIn())
    const result = await loadProfile(auth)
    expect(result).toEqual({
      kind: 'loaded',
      stored: { version: 3, profile: full },
    })
    const init = spy.mock.calls[0]![1]!
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer access-1',
    )
  })

  it('reports a failure with a fixed text, never the body', async () => {
    stubFetch(500, { detail: 'SECRET-MARKER' })
    const result = await loadProfile(fakeAuth(signedIn()))
    expect(result).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })

  it('reports an unreachable service', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network')
      }),
    )
    const result = await loadProfile(fakeAuth(signedIn()))
    expect(result).toMatchObject({ kind: 'failed' })
  })

  it('reports unauthorized after a final 401', async () => {
    stubFetch(401)
    const result = await loadProfile(fakeAuth(signedIn()))
    expect(result).toEqual({ kind: 'unauthorized' })
  })
})

describe('saveProfile', () => {
  it('puts the version and the cleaned profile', async () => {
    const spy = stubFetch(200, { version: 4, profile: full })
    const result = await saveProfile(fakeAuth(signedIn()), 3, full)
    expect(result).toMatchObject({ kind: 'saved', stored: { version: 4 } })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/profile')
    expect(init!.method).toBe('PUT')
    expect(JSON.parse(init!.body as string)).toEqual({
      version: 3,
      profile: full,
    })
    expect(new Headers(init!.headers).get('Content-Type')).toBe(
      'application/json',
    )
  })

  it('maps 400 to field messages', async () => {
    stubFetch(400, {
      error: 'validation_failed',
      fields: { 'experience[0].title': 'must not be blank' },
    })
    expect(await saveProfile(fakeAuth(signedIn()), 0, full)).toEqual({
      kind: 'invalid',
      fields: { 'experience[0].title': 'must not be blank' },
    })
  })

  it('maps 409 to conflict, 413 to a size message, other statuses to the generic one', async () => {
    stubFetch(409, { error: 'version_conflict' })
    expect(await saveProfile(fakeAuth(signedIn()), 0, full)).toEqual({
      kind: 'conflict',
    })
    stubFetch(413)
    expect(await saveProfile(fakeAuth(signedIn()), 0, full)).toEqual({
      kind: 'failed',
      message: 'The profile is too large to save.',
    })
    stubFetch(500)
    expect(await saveProfile(fakeAuth(signedIn()), 0, full)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})
