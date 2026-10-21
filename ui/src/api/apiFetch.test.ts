import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, isApiPath, type TokenSource } from './apiFetch.ts'

function source(over: Partial<TokenSource> = {}): TokenSource {
  return {
    getAccessToken: vi.fn(() => 'token-1'),
    renew: vi.fn(async () => 'token-2'),
    discard: vi.fn(async () => {}),
    ...over,
  }
}

function stubFetch(...statuses: number[]) {
  const queue = [...statuses]
  const fn = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response('body', { status: queue.shift() ?? 200 }),
  )
  vi.stubGlobal('fetch', fn)
  return fn
}

const authHeader = (call: unknown[]) =>
  new Headers((call[1] as RequestInit).headers).get('Authorization')

afterEach(() => vi.unstubAllGlobals())

describe('apiFetch', () => {
  it('sends the access token to a path on the page origin', async () => {
    const fetchSpy = stubFetch(200)
    const res = await apiFetch(source(), '/api/')
    expect(res.status).toBe(200)
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/')
    expect(authHeader(fetchSpy.mock.calls[0])).toBe('Bearer token-1')
    expect((fetchSpy.mock.calls[0][1] as RequestInit).credentials).toBe('omit')
  })

  it('renews once and repeats the call with the new token after a 401', async () => {
    const fetchSpy = stubFetch(401, 200)
    const auth = source()
    const res = await apiFetch(auth, '/api/postings')
    expect(res.status).toBe(200)
    expect(auth.renew).toHaveBeenCalledTimes(1)
    expect(fetchSpy).toHaveBeenCalledTimes(2)
    expect(authHeader(fetchSpy.mock.calls[1])).toBe('Bearer token-2')
    expect(auth.discard).not.toHaveBeenCalled()
  })

  it('signs the user out when the repeated call is still 401', async () => {
    const fetchSpy = stubFetch(401, 401)
    const auth = source()
    await expect(apiFetch(auth, '/api/x')).rejects.toMatchObject({
      kind: 'unauthorized',
    })
    expect(fetchSpy).toHaveBeenCalledTimes(2)
    expect(auth.discard).toHaveBeenCalledTimes(1)
  })

  it('signs the user out when the renewal fails, without repeating the call', async () => {
    const fetchSpy = stubFetch(401)
    const auth = source({ renew: vi.fn(async () => null) })
    await expect(apiFetch(auth, '/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    expect(auth.discard).toHaveBeenCalledTimes(1)
  })

  it('does not call the API without a token', async () => {
    const fetchSpy = stubFetch(200)
    await expect(
      apiFetch(source({ getAccessToken: () => null }), '/api/'),
    ).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('maps a connection failure to unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('network'))),
    )
    await expect(apiFetch(source(), '/api/')).rejects.toMatchObject({
      kind: 'unavailable',
    })
  })

  it('returns other statuses to the caller', async () => {
    stubFetch(404)
    expect((await apiFetch(source(), '/api/')).status).toBe(404)
  })

  it.each([
    'https://evil.example/api/x',
    '//evil.example/api/x',
    '/auth/api/register',
    '/api',
    '/other',
    '/api/../auth/x',
    '/api/a\\b',
  ])('refuses %s without sending anything', async (path) => {
    const fetchSpy = stubFetch(200)
    await expect(apiFetch(source(), path)).rejects.toBeInstanceOf(TypeError)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})

describe('isApiPath', () => {
  it.each(['/api/', '/api/postings?x=1', '/api/a/b'])('accepts %s', (p) => {
    expect(isApiPath(p)).toBe(true)
  })
})
