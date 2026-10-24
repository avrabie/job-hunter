import { afterEach, describe, expect, it, vi } from 'vitest'
import { listRepositories } from './repositoriesApi.ts'

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

describe('listRepositories', () => {
  it('gets the listing for the provider and username and returns the repositories', async () => {
    const repository = {
      provider: 'GITHUB',
      fullName: 'octocat/hello-world',
      name: 'hello-world',
      url: 'https://github.com/octocat/hello-world',
      description: 'My first repository',
      language: 'Kotlin',
      topics: ['demo'],
      stars: 42,
      pushedAt: '2026-09-30T12:00:00Z',
    }
    const spy = answer(200, { repositories: [repository] })
    const result = await listRepositories(auth, 'GITHUB', 'octocat')
    expect(result).toEqual({ kind: 'found', repositories: [repository] })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe(
      '/api/profile/repositories?provider=GITHUB&username=octocat',
    )
    expect(init!.method).toBeUndefined()
    expect(new Headers(init!.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })

  it('returns an empty list when the account has no repositories', async () => {
    answer(200, { repositories: [] })
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'found',
      repositories: [],
    })
  })

  it.each([
    ['invalid_username', 400, 'That username is not valid for this provider.'],
    ['account_not_found', 404, 'No account with that username was found.'],
    [
      'provider_rate_limited',
      429,
      'The provider is limiting requests right now. Please try again later.',
    ],
    [
      'provider_unavailable',
      502,
      'The provider could not be reached right now. Please try again.',
    ],
  ])('maps %s to a fixed message', async (error, status, message) => {
    answer(status, { error })
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'failed',
      message,
    })
  })

  it('maps a 429 without a code to the rate limit message', async () => {
    answer(429)
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'failed',
      message:
        'The provider is limiting requests right now. Please try again later.',
    })
  })

  it('shows a generic status message for any other failure, never the body', async () => {
    answer(500, { error: 'SECRET-MARKER' })
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })

  it('reports a connection failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network')
      }),
    )
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'failed',
      message: 'The service is unavailable right now. Please try again.',
    })
  })

  it('reports unauthorized after a final 401', async () => {
    answer(401)
    expect(await listRepositories(auth, 'GITHUB', 'octocat')).toEqual({
      kind: 'unauthorized',
    })
  })
})
