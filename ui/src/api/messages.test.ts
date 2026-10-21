import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from './apiFetch.ts'
import { messageForError, messageForStatus } from './messages.ts'

const consoleSpies = ['log', 'info', 'warn', 'error', 'debug'].map((m) =>
  vi.spyOn(console, m as 'log').mockImplementation(() => {}),
)

beforeEach(() => consoleSpies.forEach((s) => s.mockClear()))
afterEach(() => vi.unstubAllGlobals())

describe('messages', () => {
  it.each([
    [404, 'The backend has no endpoints yet.'],
    [502, 'The service is unavailable right now. Please try again.'],
    [503, 'The service is unavailable right now. Please try again.'],
    [504, 'The service is unavailable right now. Please try again.'],
    [500, 'The request failed (status 500).'],
  ])('status %i reads "%s"', (status, text) => {
    expect(messageForStatus(status)).toBe(text)
  })

  it('treats a connection failure as unavailable', () => {
    expect(messageForError(new ApiError('unavailable'))).toMatch(/unavailable/)
    expect(messageForError(new Error('boom'))).toBe(
      'Something went wrong. Please try again.',
    )
  })

  it('writes nothing to the console and shows no body, whatever the response says', async () => {
    const secretBody = '{"detail":"secret-body ada@example.ch token-xyz"}'
    for (const status of [404, 500, 502, 503]) {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => new Response(secretBody, { status })),
      )
      const res = await apiFetch(
        {
          getAccessToken: () => 'token-xyz',
          renew: async () => null,
          discard: async () => {},
        },
        '/api/',
      )
      expect(messageForStatus(res.status)).not.toMatch(
        /secret-body|ada@|token-xyz/,
      )
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('network token-xyz'))),
    )
    await expect(
      apiFetch(
        {
          getAccessToken: () => 'token-xyz',
          renew: async () => null,
          discard: async () => {},
        },
        '/api/',
      ),
    ).rejects.toSatisfy(
      (e: ApiError) => !messageForError(e).includes('token-xyz'),
    )
    consoleSpies.forEach((s) => expect(s).not.toHaveBeenCalled())
  })
})
