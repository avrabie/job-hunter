import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadPublicCvState, publicCvAddress } from './publicCvNotice.ts'

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}

const answer = (status: number, body: unknown = {}) => {
  const spy = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(JSON.stringify(body), { status }),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

afterEach(() => vi.unstubAllGlobals())

describe('loadPublicCvState', () => {
  it('reads GET /api/cv/public and reports a live page with its handle', async () => {
    const spy = answer(200, {
      configured: true,
      published: true,
      handle: 'ada-example',
    })
    expect(await loadPublicCvState(auth)).toEqual({
      state: 'live',
      handle: 'ada-example',
    })
    expect(spy.mock.calls[0]![0]).toBe('/api/cv/public')
  })

  it('reports not-live when there is no page or it is paused', async () => {
    answer(200, { configured: false })
    expect(await loadPublicCvState(auth)).toEqual({ state: 'not-live' })
    answer(200, { configured: true, published: false, handle: 'ada-example' })
    expect(await loadPublicCvState(auth)).toEqual({ state: 'not-live' })
  })

  it('is unknown for a failed status, a bad body or a connection failure, and never throws', async () => {
    answer(500, { error: 'boom' })
    expect(await loadPublicCvState(auth)).toEqual({ state: 'unknown' })
    answer(200, { configured: true, published: true })
    expect(await loadPublicCvState(auth)).toEqual({ state: 'unknown' })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('offline')
      }),
    )
    expect(await loadPublicCvState(auth)).toEqual({ state: 'unknown' })
  })

  it('is unknown without a token', async () => {
    const spy = answer(200, { configured: false })
    const signedOut = { ...auth, getAccessToken: () => null }
    expect(await loadPublicCvState(signedOut)).toEqual({ state: 'unknown' })
    expect(spy).not.toHaveBeenCalled()
  })
})

describe('publicCvAddress', () => {
  it('is the origin of the page and the handle under /cv/', () => {
    expect(publicCvAddress('ada-example')).toBe(
      `${window.location.origin}/cv/ada-example`,
    )
  })
})
