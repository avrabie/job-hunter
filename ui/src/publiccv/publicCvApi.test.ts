import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  checkHandle,
  getPublicCv,
  handleReasonMessage,
  previewPublicCv,
  removePublicCv,
  savePublicCv,
} from './publicCvApi.ts'
import { defaultSettings, type PublicCvSettings } from './options.ts'

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}

const answer = (
  status: number,
  body: BodyInit | null,
  headers: HeadersInit = {},
) => {
  const spy = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(body, { status, headers }),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

const json = (body: unknown) => JSON.stringify(body)

const saved = {
  configured: true,
  handle: 'ada-example',
  published: true,
  locked: false,
  settings: defaultSettings(),
  handleChangeableAt: null,
  updatedAt: '2026-10-01T10:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

describe('getPublicCv', () => {
  it('gets the page state with the token', async () => {
    const spy = answer(200, json(saved))
    const result = await getPublicCv(auth)

    expect(result).toEqual({ kind: 'ok', value: saved })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/cv/public')
    expect(init!.method).toBeUndefined()
    expect(new Headers(init!.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })

  it('returns the not configured state as it is', async () => {
    answer(200, json({ configured: false }))
    expect(await getPublicCv(auth)).toEqual({
      kind: 'ok',
      value: { configured: false },
    })
  })

  it('reports unauthorized when the token is refused twice', async () => {
    answer(401, '')
    const discard = vi.fn(async () => {})
    const result = await getPublicCv({ ...auth, discard })
    expect(result).toEqual({ kind: 'unauthorized' })
    expect(discard).toHaveBeenCalledOnce()
  })

  it('reports a connection failure as unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network down')
      }),
    )
    expect(await getPublicCv(auth)).toEqual({
      kind: 'failed',
      code: null,
      message: 'The service is unavailable right now. Please try again.',
    })
  })

  it('reports a status without a body as the generic status message', async () => {
    answer(500, 'stack trace with Ada Example')
    expect(await getPublicCv(auth)).toEqual({
      kind: 'failed',
      code: null,
      message: 'The request failed (status 500).',
    })
  })
})

describe('savePublicCv', () => {
  const body = {
    handle: 'ada-example',
    published: true,
    settings: { ...defaultSettings(), sections: ['EXPERIENCE' as const] },
  }

  it('puts the handle, the flag and the settings', async () => {
    const spy = answer(200, json(saved))
    const result = await savePublicCv(auth, body)

    expect(result).toEqual({ kind: 'ok', value: saved })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/cv/public')
    expect(init!.method).toBe('PUT')
    expect(JSON.parse(init!.body as string)).toEqual(body)
  })

  it.each([
    ['validation_failed', 400],
    ['handle_taken', 409],
    ['handle_reserved', 409],
    ['handle_held', 409],
    ['handle_change_too_soon', 409],
    ['name_required', 409],
    ['locked', 409],
    ['rate_limited', 429],
  ])(
    'maps the %s answer (status %i) to a fixed message and keeps the code',
    async (code, status) => {
      answer(status, json({ error: code, detail: 'Ada Example' }))
      const result = await savePublicCv(auth, body)
      expect(result).toMatchObject({ kind: 'failed', code })
      if (result.kind === 'failed') {
        expect(result.message).not.toContain('Ada Example')
        expect(result.message.length).toBeGreaterThan(0)
      }
    },
  )

  it('gives the name message for a missing name', async () => {
    answer(409, json({ error: 'name_required' }))
    expect(await savePublicCv(auth, body)).toEqual({
      kind: 'failed',
      code: 'name_required',
      message:
        'Add your full name in the Contact section of your profile first.',
    })
  })

  it('falls back to the status for a 429 without a known code', async () => {
    answer(429, json({ error: 'something_else' }))
    expect(await savePublicCv(auth, body)).toMatchObject({
      kind: 'failed',
      code: 'rate_limited',
    })
  })

  it('never shows the body of an unknown failure', async () => {
    answer(500, 'Ada Example stack trace')
    const result = await savePublicCv(auth, body)
    expect(JSON.stringify(result)).not.toContain('Ada Example')
  })
})

describe('removePublicCv', () => {
  it('sends a delete and accepts 204', async () => {
    const spy = answer(204, null)
    expect(await removePublicCv(auth)).toEqual({ kind: 'ok', value: null })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/cv/public')
    expect(init!.method).toBe('DELETE')
  })

  it('reports a failure with the fixed message', async () => {
    answer(500, '')
    expect(await removePublicCv(auth)).toEqual({
      kind: 'failed',
      code: null,
      message: 'The request failed (status 500).',
    })
  })
})

describe('checkHandle', () => {
  it('asks for the handle with the query encoded', async () => {
    const spy = answer(200, json({ available: true }))
    expect(await checkHandle(auth, 'ada-example')).toEqual({
      kind: 'ok',
      value: { available: true },
    })
    expect(spy.mock.calls[0]![0]).toBe(
      '/api/cv/public/handle?handle=ada-example',
    )
  })

  it('passes the signal on so a stale check can be cancelled', async () => {
    const spy = answer(200, json({ available: true }))
    const controller = new AbortController()
    await checkHandle(auth, 'ada-example', controller.signal)
    expect(spy.mock.calls[0]![1]!.signal).toBe(controller.signal)
  })

  it('returns the reason when the handle is not available', async () => {
    answer(200, json({ available: false, reason: 'held' }))
    expect(await checkHandle(auth, 'ada-example')).toEqual({
      kind: 'ok',
      value: { available: false, reason: 'held' },
    })
  })

  it('gives a fixed text for each reason', () => {
    expect(handleReasonMessage('reserved')).toBe(
      'This handle is reserved. Choose another one.',
    )
    expect(handleReasonMessage('taken')).toBe('This handle is already taken.')
    expect(handleReasonMessage('held')).toBe(
      'This handle was used recently and cannot be used yet.',
    )
    expect(handleReasonMessage('invalid')).toContain('3 to 30 characters')
  })
})

describe('previewPublicCv', () => {
  const settings: PublicCvSettings = {
    ...defaultSettings(),
    sections: ['SKILLS'],
  }

  it('posts only the settings on screen and returns the path', async () => {
    const spy = answer(200, json({ path: '/api/cv/public/preview/abc123' }))
    expect(await previewPublicCv(auth, settings)).toEqual({
      kind: 'ok',
      value: { path: '/api/cv/public/preview/abc123' },
    })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/cv/public/preview')
    expect(init!.method).toBe('POST')
    expect(JSON.parse(init!.body as string)).toEqual({ settings })
  })

  it('does not accept a path that is not a preview of the backend', async () => {
    answer(200, json({ path: 'https://elsewhere.example/steal' }))
    const result = await previewPublicCv(auth, settings)
    expect(result).toMatchObject({ kind: 'failed', code: null })
  })

  it('reports a rate limit with the fixed message', async () => {
    answer(429, json({ error: 'rate_limited' }))
    expect(await previewPublicCv(auth, settings)).toEqual({
      kind: 'failed',
      code: 'rate_limited',
      message:
        'You have reached the limit for this hour. Please try again later.',
    })
  })
})
