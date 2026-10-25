import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  confirmJobImport,
  previewJobImport,
  type JobDraft,
} from './jobImportApi.ts'

const DRAFT_ID = '9d2b7c4e-1f3a-4b5c-8d6e-7f8091a2b3c4'

const draft: JobDraft = {
  draftId: DRAFT_ID,
  title: 'Backend Engineer',
  company: 'Employer1 AG',
  location: 'Zurich',
  description: 'Invented job text',
  language: 'en',
}

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null as string | null),
  discard: vi.fn(async () => {}),
}

const answer = (status: number, body: unknown = {}) =>
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), { status })),
  )

const lastCall = () =>
  (fetch as ReturnType<typeof vi.fn>).mock.calls.at(-1) as [string, RequestInit]

afterEach(() => vi.unstubAllGlobals())

beforeEach(() => vi.clearAllMocks())

describe('previewJobImport', () => {
  it('posts the URL and returns the draft', async () => {
    answer(200, draft)
    expect(
      await previewJobImport(auth, { url: 'https://jobs.example.org/1' }),
    ).toEqual({ kind: 'previewed', draft })
    const [url, init] = lastCall()
    expect(url).toBe('/api/job-imports/preview')
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Content-Type')).toBe(
      'application/json',
    )
    expect(JSON.parse(init.body as string)).toEqual({
      url: 'https://jobs.example.org/1',
    })
  })

  it('posts the pasted text together with the URL', async () => {
    answer(200, draft)
    await previewJobImport(auth, {
      url: 'https://jobs.example.org/1',
      text: 'Pasted',
    })
    expect(JSON.parse(lastCall()[1].body as string)).toEqual({
      url: 'https://jobs.example.org/1',
      text: 'Pasted',
    })
  })

  it('sends the bearer token', async () => {
    answer(200, draft)
    await previewJobImport(auth, { url: 'https://jobs.example.org/1' })
    expect(new Headers(lastCall()[1].headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })

  it('returns the error code and its fixed message, never the body', async () => {
    answer(422, { error: 'not_a_job', secret: 'SECRET-BODY' })
    expect(
      await previewJobImport(auth, { url: 'https://jobs.example.org/1' }),
    ).toEqual({
      kind: 'failed',
      code: 'not_a_job',
      message: 'This page does not seem to be a single job posting.',
    })
  })

  it('treats an unknown code as no code and uses the status text', async () => {
    answer(400, { error: 'SECRET-CODE' })
    expect(
      await previewJobImport(auth, { url: 'https://jobs.example.org/1' }),
    ).toEqual({
      kind: 'failed',
      code: null,
      message: 'The request failed (status 400).',
    })
  })

  it('maps a 502 without a code to the unavailable text', async () => {
    answer(502, { secret: 'SECRET-BODY' })
    expect(
      await previewJobImport(auth, { url: 'https://jobs.example.org/1' }),
    ).toEqual({
      kind: 'failed',
      code: null,
      message: 'The service is unavailable right now. Please try again.',
    })
  })

  it('reports unauthorized without calling the API when signed out', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const signedOut = { ...auth, getAccessToken: () => null }
    expect(
      await previewJobImport(signedOut, { url: 'https://jobs.example.org/1' }),
    ).toEqual({ kind: 'unauthorized' })
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})

describe('confirmJobImport', () => {
  const edits = {
    title: 'Backend Engineer',
    company: 'Employer1 AG',
    location: 'Zurich',
    description: 'Invented job text',
  }

  it('posts the draft id and the edits and returns the import', async () => {
    const imported = {
      applicationId: '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b',
      created: true,
    }
    answer(201, imported)
    expect(await confirmJobImport(auth, DRAFT_ID, edits)).toEqual({
      kind: 'imported',
      job: imported,
    })
    const [url, init] = lastCall()
    expect(url).toBe('/api/job-imports')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      draftId: DRAFT_ID,
      ...edits,
    })
  })

  it('maps draft_not_found to its fixed message', async () => {
    answer(404, { error: 'draft_not_found' })
    expect(await confirmJobImport(auth, DRAFT_ID, edits)).toEqual({
      kind: 'failed',
      code: 'draft_not_found',
      message: 'This preview has expired. Preview the URL again.',
    })
  })

  it.each([
    [
      'invalid_edit',
      'Check the title, company and description: they must not be empty, too long or contain markup.',
    ],
    ['application_limit', 'You have reached the limit of tracked jobs.'],
  ])('maps %s to its fixed message', async (code, message) => {
    answer(422, { error: code, secret: 'SECRET-BODY' })
    expect(await confirmJobImport(auth, DRAFT_ID, edits)).toEqual({
      kind: 'failed',
      code,
      message,
    })
  })

  it('reports unauthorized without calling the API when signed out', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const signedOut = { ...auth, getAccessToken: () => null }
    expect(await confirmJobImport(signedOut, DRAFT_ID, edits)).toEqual({
      kind: 'unauthorized',
    })
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
