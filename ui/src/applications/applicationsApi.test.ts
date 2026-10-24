import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  listApplications,
  loadApplication,
  loadPostingStages,
  removeApplication,
  trackPosting,
  updateApplication,
  type ApplicationDetail,
  type ApplicationSummary,
} from './applicationsApi.ts'

const ID = '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b'
const POSTING = '11111111-1111-1111-1111-111111111111'

const summary: ApplicationSummary = {
  id: ID,
  postingId: POSTING,
  title: 'Backend Engineer',
  company: 'Employer1 AG',
  stage: 'INTERESTED',
  appliedOn: null,
  stageChangedAt: '2026-10-23T10:00:00Z',
  liveStatus: 'OPEN',
}

const detail: ApplicationDetail = {
  ...summary,
  url: 'https://example.org/jobs/1',
  description: 'Invented job text',
  capturedAt: '2026-10-22T10:00:00Z',
  postingLastSeen: '2026-10-23T10:00:00Z',
  notes: 'Call on Monday',
  letterCount: 0,
}

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null as string | null),
  discard: vi.fn(async () => {}),
}

const answer = (status: number, body: unknown = {}) =>
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      status === 204
        ? new Response(null, { status })
        : new Response(JSON.stringify(body), { status }),
    ),
  )

const lastCall = () =>
  (fetch as ReturnType<typeof vi.fn>).mock.calls.at(-1) as [string, RequestInit]

afterEach(() => vi.unstubAllGlobals())

beforeEach(() => vi.clearAllMocks())

describe('listApplications', () => {
  it('maps 200 to the applications', async () => {
    answer(200, [summary])
    expect(await listApplications(auth)).toEqual({
      kind: 'loaded',
      applications: [summary],
    })
    expect(lastCall()[0]).toBe('/api/applications')
  })

  it('reports unauthorized without calling the API when signed out', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const signedOut = { ...auth, getAccessToken: () => null }
    expect(await listApplications(signedOut)).toEqual({
      kind: 'unauthorized',
    })
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('uses a fixed message for a server error and never shows the body', async () => {
    answer(500, { secret: 'SECRET-BODY' })
    expect(await listApplications(auth)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('loadApplication', () => {
  it('maps 200 to the detail', async () => {
    answer(200, detail)
    expect(await loadApplication(auth, ID)).toEqual({
      kind: 'loaded',
      application: detail,
    })
    expect(lastCall()[0]).toBe(`/api/applications/${ID}`)
  })

  it('maps 404 to not found', async () => {
    answer(404, {})
    expect(await loadApplication(auth, ID)).toEqual({ kind: 'notFound' })
  })

  it('uses a fixed message for a server error', async () => {
    answer(500, { secret: 'SECRET-BODY' })
    expect(await loadApplication(auth, ID)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('trackPosting', () => {
  it('posts the posting id only and returns the application', async () => {
    answer(201, summary)
    expect(await trackPosting(auth, POSTING)).toEqual({
      kind: 'tracked',
      application: summary,
    })
    const [url, init] = lastCall()
    expect(url).toBe('/api/applications')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ postingId: POSTING })
  })

  it('shows the limit text for the application_limit code', async () => {
    answer(409, { error: 'application_limit' })
    expect(await trackPosting(auth, POSTING)).toEqual({
      kind: 'failed',
      message:
        'You can keep at most 500 applications. Remove one to track another.',
    })
  })

  it('says the posting is gone for 404', async () => {
    answer(404, {})
    expect(await trackPosting(auth, POSTING)).toEqual({
      kind: 'failed',
      message: 'That posting no longer exists.',
    })
  })

  it('uses a fixed message for a server error and never shows the body', async () => {
    answer(500, { error: 'unknown_code', secret: 'SECRET-BODY' })
    expect(await trackPosting(auth, POSTING)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('updateApplication', () => {
  it('sends only the changed fields', async () => {
    answer(200, detail)
    expect(await updateApplication(auth, ID, { notes: 'New' })).toEqual({
      kind: 'done',
    })
    const [url, init] = lastCall()
    expect(url).toBe(`/api/applications/${ID}`)
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body as string)).toEqual({ notes: 'New' })
  })

  it('sends a stage and a cleared date as null', async () => {
    answer(200, detail)
    await updateApplication(auth, ID, { stage: 'APPLIED', appliedOn: null })
    expect(JSON.parse(lastCall()[1].body as string)).toEqual({
      stage: 'APPLIED',
      appliedOn: null,
    })
  })

  it('maps 400 to a fixed message', async () => {
    answer(400, { error: 'notes_too_long', secret: 'SECRET-BODY' })
    expect(await updateApplication(auth, ID, { notes: 'x' })).toEqual({
      kind: 'failed',
      message: 'The stage, notes or date were not accepted.',
    })
  })

  it('maps 404 to a fixed message', async () => {
    answer(404, {})
    expect(await updateApplication(auth, ID, { stage: 'REJECTED' })).toEqual({
      kind: 'failed',
      message: 'That application no longer exists.',
    })
  })
})

describe('removeApplication', () => {
  it('sends DELETE and maps 204 to done', async () => {
    answer(204)
    expect(await removeApplication(auth, ID)).toEqual({ kind: 'done' })
    const [url, init] = lastCall()
    expect(url).toBe(`/api/applications/${ID}`)
    expect(init.method).toBe('DELETE')
  })

  it('maps 404 to a fixed message', async () => {
    answer(404, {})
    expect(await removeApplication(auth, ID)).toEqual({
      kind: 'failed',
      message: 'That application no longer exists.',
    })
  })
})

describe('loadPostingStages', () => {
  it('asks for the ids in one query and keeps well-formed entries only', async () => {
    answer(200, [
      { postingId: POSTING, applicationId: ID, stage: 'APPLIED' },
      { postingId: 'x', applicationId: ID, stage: 'UNKNOWN' },
      { postingId: 'y' },
      'junk',
    ])
    expect(await loadPostingStages(auth, [POSTING, 'other'])).toEqual({
      kind: 'loaded',
      stages: [{ postingId: POSTING, applicationId: ID, stage: 'APPLIED' }],
    })
    expect(lastCall()[0]).toBe(
      `/api/applications/by-posting?ids=${encodeURIComponent(`${POSTING},other`)}`,
    )
  })

  it('treats a body that is not a list as no stages', async () => {
    answer(200, { nope: true })
    expect(await loadPostingStages(auth, [POSTING])).toEqual({
      kind: 'loaded',
      stages: [],
    })
  })

  it('uses a fixed message for a server error', async () => {
    answer(500, { secret: 'SECRET-BODY' })
    expect(await loadPostingStages(auth, [POSTING])).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})
