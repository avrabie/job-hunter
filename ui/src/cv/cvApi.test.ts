import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  applyDraft,
  deleteCv,
  extractProfile,
  loadCv,
  uploadCv,
} from './cvApi.ts'
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

describe('loadCv', () => {
  it('maps 200 to the details and 404 to none', async () => {
    const cv = {
      fileName: 'cv.pdf',
      contentType: 'application/pdf',
      sizeBytes: 5,
      uploadedAt: '2026-10-23T10:00:00Z',
    }
    answer(200, cv)
    expect(await loadCv(auth)).toEqual({ kind: 'loaded', cv })
    answer(404)
    expect(await loadCv(auth)).toEqual({ kind: 'none' })
  })
  it('uses a fixed message for a server error', async () => {
    answer(500, { secret: 'SECRET-BODY' })
    expect(await loadCv(auth)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('uploadCv', () => {
  it('sends the file as multipart with the token, to the CV path only', async () => {
    answer(200, { fileName: 'cv.txt' })
    await uploadCv(auth, new File(['hello'], 'cv.txt'))
    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toBe('/api/cv')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })
  it.each([
    [400, 'The file must be a PDF, a Markdown file or a plain text file.'],
    [413, 'The file is larger than 5 MB.'],
    [500, 'The request failed (status 500).'],
  ])('maps %i to a fixed message', async (status, message) => {
    answer(status, { error: 'x', message: 'SERVER-TEXT' })
    expect(await uploadCv(auth, new File(['x'], 'cv.txt'))).toEqual({
      kind: 'failed',
      message,
    })
  })
})

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

describe('deleteCv', () => {
  it('maps 204 to deleted', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    )
    expect(await deleteCv(auth)).toEqual({ kind: 'deleted' })
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
