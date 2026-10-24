import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  deleteLetter,
  downloadLetter,
  generateLetter,
  LETTER_MESSAGES,
  listLetters,
  readLetter,
  saveEditedLetter,
  type GenerateRequest,
} from './lettersApi.ts'

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}
const answer = (status: number, body: unknown = {}, headers = {}) => {
  const spy = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(
        body === null || typeof body === 'string' ? body : JSON.stringify(body),
        {
          status,
          headers,
        },
      ),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

const request: GenerateRequest = {
  language: 'DE',
  experience: [0, 2],
  projects: [],
  skills: [1],
  instructions: 'Keep it short.',
  basedOnVersion: 1,
  comments: {
    general: 'Shorter.',
    paragraphs: [{ n: 2, text: 'Mention Kafka' }],
  },
}
const detail = {
  version: 2,
  how: 'REGENERATED',
  language: 'DE',
  createdAt: '2026-10-24T12:00:00Z',
  basedOnVersion: 1,
  text: 'Sehr geehrte Damen und Herren',
  selection: { experience: [0, 2], projects: [], skills: [1] },
  instructions: null,
  comments: null,
}

afterEach(() => vi.unstubAllGlobals())

describe('generateLetter', () => {
  it('posts the request with the token and returns the version', async () => {
    const spy = answer(201, detail)
    const result = await generateLetter(auth, 'app-1', request)
    expect(result).toEqual({ kind: 'done', letter: detail })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/applications/app-1/letters')
    expect(init!.method).toBe('POST')
    expect(JSON.parse(init!.body as string)).toEqual(request)
    expect(new Headers(init!.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })

  it.each([
    [400, 'selection_invalid'],
    [400, 'letter_text_invalid'],
    [409, 'application_limit'],
    [409, 'letter_limit'],
    [503, 'letter_not_configured'],
    [502, 'letter_failed'],
    [429, 'rate_limited'],
  ] as const)('maps %i %s to its fixed message', async (status, code) => {
    answer(status, { error: code, detail: 'SECRET-BODY' })
    const result = await generateLetter(auth, 'app-1', request)
    expect(result).toEqual({
      kind: 'failed',
      code,
      message: LETTER_MESSAGES[code],
    })
  })

  it('treats a 429 without a body as rate limited', async () => {
    answer(429, 'nope')
    const result = await generateLetter(auth, 'app-1', request)
    expect(result).toMatchObject({ kind: 'failed', code: 'rate_limited' })
  })

  it('falls back to the status message for an unknown error', async () => {
    answer(500, { error: 'boom' })
    const result = await generateLetter(auth, 'app-1', request)
    expect(result).toEqual({
      kind: 'failed',
      code: null,
      message: 'The request failed (status 500).',
    })
  })

  it('reports an unreachable service without a body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network')
      }),
    )
    const result = await generateLetter(auth, 'app-1', request)
    expect(result.kind).toBe('failed')
  })
})

describe('other letter calls', () => {
  it('saves an edited text', async () => {
    const spy = answer(201, { ...detail, how: 'EDITED' })
    const result = await saveEditedLetter(auth, 'app-1', 'My text')
    expect(result.kind).toBe('done')
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/applications/app-1/letters/edited')
    expect(JSON.parse(init!.body as string)).toEqual({ text: 'My text' })
  })

  it('lists and reads versions', async () => {
    let spy = answer(200, [{ version: 1 }])
    expect(await listLetters(auth, 'app-1')).toEqual({
      kind: 'loaded',
      letters: [{ version: 1 }],
    })
    expect(spy.mock.calls[0]![0]).toBe('/api/applications/app-1/letters')
    spy = answer(200, detail)
    expect((await readLetter(auth, 'app-1', 2)).kind).toBe('done')
    expect(spy.mock.calls[0]![0]).toBe('/api/applications/app-1/letters/2')
  })

  it('deletes a version', async () => {
    const spy = answer(204, null as never)
    expect(await deleteLetter(auth, 'app-1', 2)).toEqual({ kind: 'done' })
    expect(spy.mock.calls[0]![1]!.method).toBe('DELETE')
  })

  it('downloads the bytes with the plain file name from the server', async () => {
    const spy = answer(200, '# Letter', {
      'Content-Disposition':
        'attachment; filename="motivation-employer1-dev.md"',
    })
    const result = await downloadLetter(auth, 'app-1', 2)
    expect(spy.mock.calls[0]![0]).toBe(
      '/api/applications/app-1/letters/2/download',
    )
    if (result.kind !== 'done') throw new Error('not done')
    expect(result.fileName).toBe('motivation-employer1-dev.md')
    expect(await result.blob.text()).toBe('# Letter')
  })

  it('uses a fallback file name when the header is odd', async () => {
    answer(200, 'x', { 'Content-Disposition': 'attachment; filename="../a"' })
    const result = await downloadLetter(auth, 'app-1', 1)
    expect(result).toMatchObject({ fileName: 'motivation-letter.md' })
  })

  it('returns unauthorized when there is no token', async () => {
    const result = await listLetters(
      { ...auth, getAccessToken: () => null },
      'app-1',
    )
    expect(result).toEqual({ kind: 'unauthorized' })
  })
})
