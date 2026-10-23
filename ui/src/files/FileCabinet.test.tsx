import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { emptyProfile, type Profile } from '../profile/types.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { FileCabinet } from './FileCabinet.tsx'
import { FakeXhr, stubXhr } from './fakeXhr.ts'
import type { FileInfo } from './filesApi.ts'

const cv: FileInfo = {
  id: '0b6f2c1e-0000-4000-8000-000000000001',
  name: 'Jane CV.pdf',
  kind: 'CV',
  contentType: 'application/pdf',
  sizeBytes: 20480,
  uploadedAt: '2026-10-23T10:00:00Z',
  master: true,
}

const photos: FileInfo = {
  id: '0b6f2c1e-0000-4000-8000-000000000002',
  name: 'Holiday photos.zip',
  kind: 'OTHER',
  contentType: 'application/zip',
  sizeBytes: 2.5 * 1024 * 1024,
  uploadedAt: '2026-10-20T08:00:00Z',
  master: false,
}

const otherCv: FileInfo = {
  id: '0b6f2c1e-0000-4000-8000-000000000003',
  name: 'Old CV.md',
  kind: 'CV',
  contentType: 'text/markdown',
  sizeBytes: 900,
  uploadedAt: '2026-09-01T08:00:00Z',
  master: false,
}

type Call = { method: string; url: string; body: unknown; auth: string | null }
type Route = (call: Call) => Response | undefined

const json = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status })

/** Routes `/api/files...`; `extra` answers first. Returns the recorded calls. */
function stubApi(extra: Route) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const call: Call = {
        method: init?.method ?? 'GET',
        url,
        body:
          init?.body instanceof FormData
            ? 'form-data'
            : init?.body
              ? JSON.parse(init.body as string)
              : undefined,
        auth: new Headers(init?.headers).get('Authorization'),
      }
      calls.push(call)
      return extra(call) ?? json(404)
    }),
  )
  return calls
}

const listing =
  (files: FileInfo[]): Route =>
  (c) =>
    c.method === 'GET' && c.url === '/api/files' ? json(200, files) : undefined

const noDraft = vi.fn()

function mount(
  auth: AuthApi = fakeAuth(signedIn()),
  props: { formHasContent?: boolean; onDraft?: (draft: Profile) => void } = {},
) {
  return render(
    <AuthContext.Provider value={auth}>
      <FileCabinet
        formHasContent={props.formHasContent ?? false}
        onDraft={props.onDraft ?? noDraft}
      />
    </AuthContext.Provider>,
  )
}

const row = (name: string) => screen.getByText(name).closest('li')!

let location: { assign: ReturnType<typeof vi.fn> }
let xhr: typeof FakeXhr

beforeEach(() => {
  location = { assign: vi.fn() }
  vi.stubGlobal('location', location)
  xhr = stubXhr()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('file list', () => {
  it('says there are no files and offers the upload', async () => {
    stubApi(listing([]))
    mount()
    expect(await screen.findByText(/You have no files yet/)).toBeInTheDocument()
    expect(screen.getByLabelText('Upload a file')).toBeInTheDocument()
  })

  it('lists both files with the master label and readable sizes', async () => {
    stubApi(listing([cv, photos]))
    mount()
    expect(await screen.findByText('Jane CV.pdf')).toBeInTheDocument()
    expect(row('Jane CV.pdf')).toHaveTextContent('CV · 20 KB')
    expect(
      within(row('Jane CV.pdf')).getByText('Master CV'),
    ).toBeInTheDocument()
    expect(row('Holiday photos.zip')).toHaveTextContent('Other file · 2.5 MB')
    expect(
      within(row('Holiday photos.zip')).queryByText('Master CV'),
    ).toBeNull()
  })

  it('shows sizes in GB for large files', async () => {
    stubApi(listing([{ ...photos, sizeBytes: 3 * 1024 ** 3 }]))
    mount()
    expect(await screen.findByText(/3\.00 GB/)).toBeInTheDocument()
  })

  it('shows a loading state, then an error with a retry', async () => {
    let answer = 500
    stubApi((c) =>
      c.method === 'GET' && c.url === '/api/files'
        ? json(answer, answer === 200 ? [cv] : {})
        : undefined,
    )
    mount()
    expect(screen.getByText('Loading your files…')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    answer = 200
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Jane CV.pdf')).toBeInTheDocument()
  })
})

describe('upload', () => {
  it('uploads an other file with progress and lists it afterwards', async () => {
    stubApi(listing([]))
    mount()
    await screen.findByText(/You have no files yet/)
    await userEvent.selectOptions(screen.getByLabelText('Kind'), 'Other file')
    await userEvent.upload(
      screen.getByLabelText('Upload a file'),
      new File(['zip'], 'Holiday photos.zip'),
    )

    await waitFor(() => expect(xhr.instances).toHaveLength(1))
    expect(xhr.instances[0]!.body).toBeInstanceOf(FormData)
    expect((xhr.instances[0]!.body as FormData).get('kind')).toBe('OTHER')
    expect(
      screen.getByText('Uploading Holiday photos.zip…'),
    ).toBeInTheDocument()
    xhr.instances[0]!.progress(50, 100)
    await waitFor(() =>
      expect(
        (screen.getByRole('progressbar') as HTMLProgressElement).value,
      ).toBe(0.5),
    )

    xhr.instances[0]!.answer(201, JSON.stringify(photos))
    expect(await screen.findByText('Holiday photos.zip')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).toBeNull()
  })

  it('cancels the upload and leaves the list unchanged', async () => {
    stubApi(listing([cv]))
    mount()
    await screen.findByText('Jane CV.pdf')
    await userEvent.selectOptions(screen.getByLabelText('Kind'), 'Other file')
    await userEvent.upload(
      screen.getByLabelText('Upload a file'),
      new File(['zip'], 'Holiday photos.zip'),
    )
    await waitFor(() => expect(xhr.instances).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(xhr.instances[0]!.aborted).toBe(true)
    await waitFor(() => expect(screen.queryByRole('progressbar')).toBeNull())
    expect(screen.queryByText('Holiday photos.zip')).toBeNull()
    expect(screen.getByText('Jane CV.pdf')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it.each([
    ['CV', 'Jane CV.pdf', 413, 'The file is larger than 5 MB.'],
    [
      'OTHER',
      'Holiday photos.zip',
      507,
      'The server has no room for this file right now.',
    ],
  ] as const)(
    'shows a fixed message for %s file answered %i and keeps the list',
    async (kind, name, status, message) => {
      stubApi(listing([cv]))
      mount()
      await screen.findByText('Jane CV.pdf')
      await userEvent.selectOptions(
        screen.getByLabelText('Kind'),
        kind === 'CV' ? 'CV' : 'Other file',
      )
      await userEvent.upload(
        screen.getByLabelText('Upload a file'),
        new File(['x'], name),
      )
      await waitFor(() => expect(xhr.instances).toHaveLength(1))
      xhr.instances[0]!.answer(status, '{"message":"SERVER-TEXT"}')
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByText('Jane CV.pdf')).toBeInTheDocument()
      expect(screen.getAllByRole('listitem')).toHaveLength(1)
      expect(screen.queryByText('SERVER-TEXT')).toBeNull()
    },
  )
})

describe('download and delete', () => {
  it('asks for a ticket with the user token and points the browser at the path', async () => {
    const path = `/api/files/${cv.id}/content?ticket=abc123`
    const calls = stubApi((c) =>
      c.url === `/api/files/${cv.id}/download-ticket`
        ? json(200, { path })
        : listing([cv])(c),
    )
    mount()
    await userEvent.click(
      within(await waitForRow('Jane CV.pdf')).getByRole('button', {
        name: 'Download',
      }),
    )
    await waitFor(() => expect(location.assign).toHaveBeenCalledWith(path))
    const ticket = calls.find((c) => c.method === 'POST')!
    expect(ticket.auth).toMatch(/^Bearer /)
  })

  it('deletes after confirmation and removes the file from the list', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = stubApi((c) =>
      c.method === 'DELETE'
        ? new Response(null, { status: 204 })
        : listing([cv, photos])(c),
    )
    mount()
    await userEvent.click(
      within(await waitForRow('Holiday photos.zip')).getByRole('button', {
        name: 'Delete',
      }),
    )
    await waitFor(() =>
      expect(screen.queryByText('Holiday photos.zip')).toBeNull(),
    )
    expect(calls.find((c) => c.method === 'DELETE')?.url).toBe(
      `/api/files/${photos.id}`,
    )
    expect(screen.getByText('Jane CV.pdf')).toBeInTheDocument()
  })

  it('does nothing when the deletion is not confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const calls = stubApi(listing([photos]))
    mount()
    await userEvent.click(
      within(await waitForRow('Holiday photos.zip')).getByRole('button', {
        name: 'Delete',
      }),
    )
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    expect(screen.getByText('Holiday photos.zip')).toBeInTheDocument()
  })
})

describe('master CV', () => {
  it('moves the label to the chosen CV after the server confirms', async () => {
    const calls = stubApi((c) => {
      if (c.method === 'PUT') return new Response(null, { status: 200 })
      return listing([cv, otherCv])(c)
    })
    mount()
    await userEvent.click(
      within(await waitForRow('Old CV.md')).getByRole('button', {
        name: 'Use as master CV',
      }),
    )
    await waitFor(() =>
      expect(
        within(row('Old CV.md')).getByText('Master CV'),
      ).toBeInTheDocument(),
    )
    expect(within(row('Jane CV.pdf')).queryByText('Master CV')).toBeNull()
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      fileId: otherCv.id,
    })
  })

  it('removes the master and shows no label', async () => {
    const calls = stubApi((c) => {
      if (c.method === 'DELETE' && c.url === '/api/files/master')
        return new Response(null, { status: 204 })
      return listing([cv])(c)
    })
    mount()
    await userEvent.click(
      within(await waitForRow('Jane CV.pdf')).getByRole('button', {
        name: 'Remove master',
      }),
    )
    await waitFor(() => expect(screen.queryByText('Master CV')).toBeNull())
    expect(
      calls.some((c) => c.url === '/api/files/master' && c.method === 'DELETE'),
    ).toBe(true)
  })
})

describe('filling the profile from the master CV', () => {
  const draft = {
    ...emptyProfile(),
    summary: 'Drafted from the CV',
    skills: [{ name: 'Kotlin', level: null }],
  }
  it('hands the draft to the page and says nothing is saved', async () => {
    const onDraft = vi.fn()
    const calls = stubApi((c) =>
      c.url === '/api/cv/extract' ? json(200, draft) : listing([cv])(c),
    )
    mount(undefined, { onDraft })
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    expect(
      await screen.findByText(/Nothing is saved until you press Save/),
    ).toBeInTheDocument()
    expect(onDraft).toHaveBeenCalledWith(
      expect.objectContaining({ summary: 'Drafted from the CV' }),
    )
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('is disabled with a hint when no file is the master', async () => {
    stubApi(listing([otherCv]))
    mount()
    expect(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    ).toBeDisabled()
    expect(
      screen.getByText(/Choose a master CV in your files/),
    ).toBeInTheDocument()
  })

  it('is disabled when the master was removed', async () => {
    stubApi(listing([{ ...cv, master: false }]))
    mount()
    expect(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    ).toBeDisabled()
  })

  it('asks before a draft replaces a form that has content', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const onDraft = vi.fn()
    const calls = stubApi((c) =>
      c.url === '/api/cv/extract' ? json(200, draft) : listing([cv])(c),
    )
    mount(undefined, { formHasContent: true, onDraft })
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    expect(window.confirm).toHaveBeenCalled()
    expect(calls.some((c) => c.url === '/api/cv/extract')).toBe(false)
    expect(onDraft).not.toHaveBeenCalled()
  })

  it.each([
    [409, 'Choose a master CV in your files first.'],
    [
      422,
      'No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.',
    ],
    [429, 'Too many extractions. Please try again later.'],
    [502, 'The CV could not be read right now.'],
  ])('shows a fixed message for %i', async (status, text) => {
    const onDraft = vi.fn()
    stubApi((c) =>
      c.url === '/api/cv/extract' ? json(status, {}) : listing([cv])(c),
    )
    mount(undefined, { onDraft })
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Fill the profile from my CV',
      }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(text)
    expect(onDraft).not.toHaveBeenCalled()
  })
})

/** Waits for a file row to show, then returns it. */
async function waitForRow(name: string): Promise<HTMLElement> {
  await screen.findByText(name)
  return row(name)
}
