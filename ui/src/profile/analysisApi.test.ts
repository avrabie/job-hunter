import { afterEach, describe, expect, it, vi } from 'vitest'
import { analyzeProject } from './analysisApi.ts'

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

const analysis = {
  summary: 'A sample.',
  architecture: 'One module.',
  techStack: [
    {
      name: 'Spring Boot',
      category: 'FRAMEWORK',
      evidencePath: 'build.gradle.kts',
      evidence: 'spring-boot gradle plugin and starter dependencies',
    },
  ],
  files: ['README.md', 'build.gradle.kts'],
  truncated: false,
  analyzedAt: '2026-10-24T12:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

describe('analyzeProject', () => {
  it('posts the provider and the full name and returns the analysis', async () => {
    const spy = answer(200, { analysis })
    const result = await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')
    expect(result).toEqual({ kind: 'analyzed', analysis })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/profile/open-source-projects/analyze')
    expect(init!.method).toBe('POST')
    expect(JSON.parse(init!.body as string)).toEqual({
      provider: 'GITHUB',
      fullName: 'octocat/hello-world',
    })
    expect(new Headers(init!.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
    expect(new Headers(init!.headers).get('Content-Type')).toBe(
      'application/json',
    )
  })

  it.each([
    [
      'project_not_found',
      404,
      'Save this project first, then analyze it. Only saved projects can be analyzed.',
    ],
    [
      'repository_not_found',
      404,
      'This repository was not found on the provider.',
    ],
    [
      'too_many_analyses',
      429,
      'You have reached the limit of analyses for this hour. Please try again later.',
    ],
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
    [
      'analysis_failed',
      502,
      'The analysis could not be completed. Please try again later.',
    ],
    [
      'analysis_not_configured',
      503,
      'Analysis is not available on this server.',
    ],
  ])('maps %s to a fixed message', async (error, status, message) => {
    answer(status, { error })
    expect(await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')).toEqual(
      { kind: 'failed', message },
    )
  })

  it('maps a 429 without a code to the rate limit message', async () => {
    answer(429)
    expect(await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')).toEqual(
      {
        kind: 'failed',
        message:
          'The provider is limiting requests right now. Please try again later.',
      },
    )
  })

  it('shows a generic status message for any other failure, never the body', async () => {
    answer(400, { error: 'invalid_request', detail: 'SECRET-MARKER' })
    expect(await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')).toEqual(
      { kind: 'failed', message: 'The request failed (status 400).' },
    )
  })

  it('reports a connection failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('network')
      }),
    )
    expect(await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')).toEqual(
      {
        kind: 'failed',
        message: 'The service is unavailable right now. Please try again.',
      },
    )
  })

  it('reports unauthorized after a final 401', async () => {
    answer(401)
    expect(await analyzeProject(auth, 'GITHUB', 'octocat/hello-world')).toEqual(
      { kind: 'unauthorized' },
    )
  })
})
