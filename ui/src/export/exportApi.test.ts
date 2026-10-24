import { afterEach, describe, expect, it, vi } from 'vitest'
import { exportCv } from './exportApi.ts'
import { defaultOptions, toRequest } from './options.ts'

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}

const answer = (status: number, body: BodyInit, headers: HeadersInit = {}) => {
  const spy = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(body, { status, headers }),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

const json = (body: unknown) => JSON.stringify(body)

afterEach(() => vi.unstubAllGlobals())

describe('exportCv', () => {
  it('posts the request with the token and returns the file under the server name', async () => {
    const spy = answer(200, '%PDF-1.4', {
      'Content-Type': 'application/pdf',
      'Content-Disposition': 'attachment; filename="ada-example-cv-en.pdf"',
    })
    const options = defaultOptions()
    const result = await exportCv(auth, options)

    expect(result.kind).toBe('exported')
    if (result.kind === 'exported') {
      expect(result.fileName).toBe('ada-example-cv-en.pdf')
      expect(await result.blob.text()).toBe('%PDF-1.4')
    }
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/cv/export')
    expect(init!.method).toBe('POST')
    expect(JSON.parse(init!.body as string)).toEqual(toRequest(options))
    expect(new Headers(init!.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })

  it('names the file from the format when the server sends no name', async () => {
    answer(200, '<html></html>')
    const options = { ...defaultOptions(), format: 'HTML' as const }
    const result = await exportCv(auth, options)
    expect(result.kind === 'exported' && result.fileName).toBe('cv.html')
  })

  it('does not send profile text or preferences: only the options', async () => {
    const spy = answer(200, 'x')
    await exportCv(auth, defaultOptions())
    const body = String(spy.mock.calls[0]![1]!.body)
    expect(Object.keys(JSON.parse(body)).sort()).toEqual(
      [
        'format',
        'includeAnalysis',
        'includeOpenSource',
        'includeWorkAuthorisation',
        'language',
        'sections',
        'template',
      ].sort(),
    )
  })

  it.each([
    [
      'invalid_request',
      400,
      'The export options were not accepted. Please try again.',
    ],
    [
      'name_required',
      409,
      'Add your full name in the Contact section of your profile first.',
    ],
    [
      'too_many_exports',
      429,
      'You have reached the limit of exports for this hour. Please try again later.',
    ],
    [
      'busy',
      503,
      'The server is busy creating PDFs right now. Please try again in a moment.',
    ],
    [
      'render_failed',
      500,
      'The CV could not be created. Please try again later.',
    ],
  ])(
    'maps the %s error to a fixed message and keeps the code',
    async (code, status, message) => {
      answer(status, json({ error: code, detail: 'Ada Example' }))
      expect(await exportCv(auth, defaultOptions())).toEqual({
        kind: 'failed',
        code,
        message,
      })
    },
  )

  it('never shows the body of a failure', async () => {
    answer(500, 'stack trace with Ada Example')
    const result = await exportCv(auth, defaultOptions())
    expect(result).toEqual({
      kind: 'failed',
      code: 'render_failed',
      message: 'The CV could not be created. Please try again later.',
    })
  })

  it('falls back to the status when the body has no known code', async () => {
    answer(429, json({ error: 'something_else' }))
    const result = await exportCv(auth, defaultOptions())
    expect(result).toMatchObject({ kind: 'failed', code: 'too_many_exports' })
  })

  it('reports a status without a known code with the generic message', async () => {
    answer(418, 'teapot')
    expect(await exportCv(auth, defaultOptions())).toEqual({
      kind: 'failed',
      code: null,
      message: 'The request failed (status 418).',
    })
  })

  it('reports a connection failure as unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network down')
      }),
    )
    expect(await exportCv(auth, defaultOptions())).toEqual({
      kind: 'failed',
      code: null,
      message: 'The service is unavailable right now. Please try again.',
    })
  })

  it('reports unauthorized when the token is refused twice', async () => {
    answer(401, '')
    const discard = vi.fn(async () => {})
    const result = await exportCv(
      { ...auth, renew: vi.fn(async () => null), discard },
      defaultOptions(),
    )
    expect(result).toEqual({ kind: 'unauthorized' })
    expect(discard).toHaveBeenCalledOnce()
  })

  it('passes the signal on so a stale preview can be cancelled', async () => {
    const spy = answer(200, 'x')
    const controller = new AbortController()
    await exportCv(auth, defaultOptions(), controller.signal)
    expect(spy.mock.calls[0]![1]!.signal).toBe(controller.signal)
  })
})
