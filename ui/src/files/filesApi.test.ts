import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearMaster,
  deleteFile,
  listFiles,
  requestDownload,
  setMaster,
  uploadFile,
  type FileInfo,
} from './filesApi.ts'
import { stubXhr, type FakeXhr } from './fakeXhr.ts'

const ID = '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b'

const file: FileInfo = {
  id: ID,
  name: 'Jane CV.pdf',
  kind: 'CV',
  contentType: 'application/pdf',
  sizeBytes: 20480,
  uploadedAt: '2026-10-23T10:00:00Z',
  master: true,
}

const auth = {
  getAccessToken: () => 'token-1',
  renew: vi.fn(async () => null as string | null),
  discard: vi.fn(async () => {}),
}

const answer = (status: number, body: unknown = {}) =>
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), { status })),
  )

let location: { assign: ReturnType<typeof vi.fn> }

beforeEach(() => {
  vi.clearAllMocks()
  location = { assign: vi.fn() }
  vi.stubGlobal('location', location)
})

afterEach(() => vi.unstubAllGlobals())

describe('listFiles', () => {
  it('maps 200 to the files', async () => {
    answer(200, [file])
    expect(await listFiles(auth)).toEqual({ kind: 'loaded', files: [file] })
  })
  it('treats 404 as a failure', async () => {
    answer(404)
    expect(await listFiles(auth)).toEqual({
      kind: 'failed',
      message: 'The backend has no endpoints yet.',
    })
  })
  it('uses a fixed message for a server error', async () => {
    answer(500, { secret: 'SECRET-BODY' })
    expect(await listFiles(auth)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 500).',
    })
  })
})

describe('deleteFile', () => {
  it('sends DELETE with the token and maps 204 to done', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    )
    expect(await deleteFile(auth, ID)).toEqual({ kind: 'done' })
    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toBe(`/api/files/${ID}`)
    expect(init.method).toBe('DELETE')
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
  })
})

describe('master CV', () => {
  it('sends the chosen id in the body', async () => {
    answer(200, file)
    expect(await setMaster(auth, ID)).toEqual({ kind: 'done' })
    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toBe('/api/files/master')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ fileId: ID })
  })
  it.each([
    [400, 'Only a CV file can be the master CV.'],
    [404, 'That file no longer exists.'],
    [500, 'The request failed (status 500).'],
  ])('maps setMaster %i to a fixed message', async (status, message) => {
    answer(status, { message: 'SERVER-TEXT' })
    expect(await setMaster(auth, ID)).toEqual({ kind: 'failed', message })
  })
  it('clears the master with DELETE', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    )
    expect(await clearMaster(auth)).toEqual({ kind: 'done' })
    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toBe('/api/files/master')
    expect(init.method).toBe('DELETE')
  })
})

describe('requestDownload', () => {
  it('asks for a ticket and points the browser at the returned path, never reading the bytes', async () => {
    const path = `/api/files/${ID}/content?ticket=abc123`
    answer(200, { path })
    const blob = vi.spyOn(Response.prototype, 'blob')

    expect(await requestDownload(auth, ID)).toEqual({ kind: 'done' })

    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toBe(`/api/files/${ID}/download-ticket`)
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer token-1',
    )
    expect(location.assign).toHaveBeenCalledWith(path)
    expect(blob).not.toHaveBeenCalled()
  })
  it('does not navigate to a path outside the file API', async () => {
    answer(200, { path: 'https://evil.example/steal' })
    const result = await requestDownload(auth, ID)
    expect(result.kind).toBe('failed')
    expect(location.assign).not.toHaveBeenCalled()
  })
  it('uses a fixed message when the ticket is refused', async () => {
    answer(403, { message: 'SERVER-TEXT' })
    expect(await requestDownload(auth, ID)).toEqual({
      kind: 'failed',
      message: 'The request failed (status 403).',
    })
    expect(location.assign).not.toHaveBeenCalled()
  })
})

describe('uploadFile', () => {
  let xhr: typeof FakeXhr

  beforeEach(() => {
    xhr = stubXhr()
  })

  const sent = () => xhr.instances[0]!

  it('posts the file and its kind to the file API with the token', () => {
    const f = new File(['hello'], 'notes.txt', { type: 'text/plain' })
    void uploadFile(auth, f, 'OTHER', () => {})
    const request = sent()
    expect(request.method).toBe('POST')
    expect(request.url).toBe('/api/files')
    expect(request.headers['Authorization']).toBe('Bearer token-1')
    const body = request.body as FormData
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('kind')).toBe('OTHER')
    expect(body.get('file')).toBeInstanceOf(File)
  })

  it('reports progress as a fraction', () => {
    const progress = vi.fn()
    void uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', progress)
    sent().progress(25, 100)
    sent().progress(100, 100)
    expect(progress.mock.calls).toEqual([[0.25], [1]])
  })

  it('resolves with the stored file on a success answer', async () => {
    const result = uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', () => {})
    sent().answer(
      201,
      JSON.stringify({ ...file, kind: 'OTHER', master: false }),
    )
    expect(await result).toEqual({
      kind: 'stored',
      file: { ...file, kind: 'OTHER', master: false },
    })
  })

  it('aborts the request through the signal and reports cancelled', async () => {
    const controller = new AbortController()
    const result = uploadFile(
      auth,
      new File(['x'], 'a.txt'),
      'OTHER',
      () => {},
      controller.signal,
    )
    controller.abort()
    expect(sent().aborted).toBe(true)
    expect(await result).toEqual({ kind: 'cancelled' })
  })

  it('sends nothing when the signal is already aborted', async () => {
    const controller = new AbortController()
    controller.abort()
    const result = await uploadFile(
      auth,
      new File(['x'], 'a.txt'),
      'OTHER',
      () => {},
      controller.signal,
    )
    expect(result).toEqual({ kind: 'cancelled' })
    expect(xhr.instances).toHaveLength(0)
  })

  it.each([
    [
      'CV',
      400,
      'The file must be a PDF, a Markdown file or a plain text file.',
    ],
    ['CV', 413, 'The file is larger than 5 MB.'],
    ['OTHER', 413, 'The file is larger than 10 GB.'],
    ['CV', 507, 'The server has no room for this file right now.'],
    ['OTHER', 507, 'The server has no room for this file right now.'],
    ['OTHER', 500, 'The request failed (status 500).'],
  ] as const)(
    'maps %s upload %i to a fixed message',
    async (kind, status, message) => {
      const result = uploadFile(auth, new File(['x'], 'a.txt'), kind, () => {})
      sent().answer(status, '{"message":"SERVER-TEXT"}')
      expect(await result).toEqual({ kind: 'failed', message })
    },
  )

  it('shows the connection message when the request fails', async () => {
    const result = uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', () => {})
    sent().fail()
    expect(await result).toEqual({
      kind: 'failed',
      message: 'The service is unavailable right now. Please try again.',
    })
  })

  it('renews the token after a 401 and repeats the upload once', async () => {
    auth.renew.mockResolvedValueOnce('token-2')
    const result = uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', () => {})
    sent().answer(401)
    await vi.waitFor(() => expect(xhr.instances).toHaveLength(2))
    expect(xhr.instances[1]!.headers['Authorization']).toBe('Bearer token-2')
    xhr.instances[1]!.answer(201, JSON.stringify(file))
    expect(await result).toEqual({ kind: 'stored', file })
  })

  it('signs out when the repeated upload is also refused', async () => {
    auth.renew.mockResolvedValueOnce('token-2')
    const discard = vi.mocked(auth.discard)
    discard.mockClear()
    const result = uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', () => {})
    sent().answer(401)
    await vi.waitFor(() => expect(xhr.instances).toHaveLength(2))
    xhr.instances[1]!.answer(401)
    expect(await result).toEqual({ kind: 'unauthorized' })
    expect(discard).toHaveBeenCalledOnce()
  })

  it('signs out without a second request when renewal fails', async () => {
    auth.renew.mockResolvedValueOnce(null)
    const discard = vi.mocked(auth.discard)
    discard.mockClear()
    const result = uploadFile(auth, new File(['x'], 'a.txt'), 'OTHER', () => {})
    sent().answer(401)
    expect(await result).toEqual({ kind: 'unauthorized' })
    expect(xhr.instances).toHaveLength(1)
    expect(discard).toHaveBeenCalledOnce()
  })
})
