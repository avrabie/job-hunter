import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadSchedule, saveSchedule } from './adminApi.ts'

const auth = {
  getAccessToken: () => 'token',
  renew: vi.fn(async () => null),
  discard: vi.fn(async () => {}),
}
const stub = (res: Response) => {
  const fn = vi.fn(async () => res)
  vi.stubGlobal('fetch', fn)
  return fn
}
afterEach(() => vi.unstubAllGlobals())

describe('loadSchedule', () => {
  it('maps 200 to loaded', async () => {
    stub(new Response(JSON.stringify({ enabled: true, cron: 'c', zone: 'z' })))
    expect(await loadSchedule(auth)).toEqual({
      kind: 'loaded',
      schedule: { enabled: true, cron: 'c', zone: 'z' },
    })
  })
  it('maps 403 to forbidden', async () => {
    stub(new Response('', { status: 403 }))
    expect(await loadSchedule(auth)).toEqual({ kind: 'forbidden' })
  })
  it('maps 401 (after a failed renewal) to unauthorized', async () => {
    stub(new Response('', { status: 401 }))
    expect(await loadSchedule(auth)).toEqual({ kind: 'unauthorized' })
  })
  it('maps 500 to the fixed failure message', async () => {
    stub(new Response('secret body', { status: 500 }))
    const r = await loadSchedule(auth)
    expect(r).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('saveSchedule', () => {
  it('posts only enabled, cron and zone', async () => {
    const fn = stub(
      new Response(JSON.stringify({ enabled: false, cron: 'c', zone: 'z' })),
    )
    await saveSchedule(auth, { enabled: false, cron: 'c', zone: 'z' })
    const [, init] = fn.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      enabled: false,
      cron: 'c',
      zone: 'z',
    })
  })
  it('maps 400 to invalid with the server field and message', async () => {
    stub(
      new Response(
        JSON.stringify({
          error: 'validation_failed',
          field: 'cron',
          message: 'too frequent',
        }),
        { status: 400 },
      ),
    )
    expect(
      await saveSchedule(auth, { enabled: true, cron: 'c', zone: 'z' }),
    ).toEqual({ kind: 'invalid', field: 'cron', message: 'too frequent' })
  })
  it('uses a generic message for an unreadable 400', async () => {
    stub(new Response('nope', { status: 400 }))
    const r = await saveSchedule(auth, { enabled: true, cron: 'c', zone: 'z' })
    expect(r).toEqual({
      kind: 'invalid',
      field: '',
      message: 'The schedule was rejected.',
    })
  })
})
