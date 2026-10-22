import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { listPath, loadPosting, loadPostings } from './postingsApi.ts'

const query = { q: '', canton: '', source: '', page: 0 }
const auth = () => fakeAuth(signedIn())

afterEach(() => vi.unstubAllGlobals())

function stub(status: number, body: unknown = []) {
  const fetchMock = vi.fn(
    async () => new Response(JSON.stringify(body), { status }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('listPath', () => {
  it('leaves empty filters out', () => {
    expect(listPath(query)).toBe('/api/postings?page=0&size=20')
  })

  it('carries the filters and the page', () => {
    const path = listPath({
      q: ' kotlin dev ',
      canton: 'ZH',
      source: 'greenhouse-proton',
      page: 2,
    })
    const params = new URL(path, 'http://x').searchParams
    expect(params.get('q')).toBe('kotlin dev')
    expect(params.get('canton')).toBe('ZH')
    expect(params.get('source')).toBe('greenhouse-proton')
    expect(params.get('page')).toBe('2')
  })
})

describe('loadPostings', () => {
  it('returns the postings', async () => {
    stub(200, [{ id: 'a' }])
    expect(await loadPostings(auth(), query)).toEqual({
      kind: 'loaded',
      postings: [{ id: 'a' }],
    })
  })

  it('maps 500 to a fixed message without the body', async () => {
    stub(500, { secret: 'BODY' })
    const result = await loadPostings(auth(), query)
    expect(result.kind).toBe('failed')
    expect(JSON.stringify(result)).not.toContain('BODY')
  })

  it('maps a lost session to unauthorized', async () => {
    stub(401)
    expect((await loadPostings(auth(), query)).kind).toBe('unauthorized')
  })
})

describe('loadPosting', () => {
  it('maps 404 to notFound and 200 to the posting', async () => {
    stub(404)
    expect((await loadPosting(auth(), 'x')).kind).toBe('notFound')
    stub(200, { id: 'x', description: 'd' })
    expect((await loadPosting(auth(), 'x')).kind).toBe('loaded')
  })

  it('encodes the id into the path', async () => {
    const fetchMock = stub(404)
    await loadPosting(auth(), '../x')
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe(
      '/api/postings/..%2Fx',
    )
  })
})
