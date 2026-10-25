import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import type { FileInfo } from '../files/filesApi.ts'
import { ExportPage } from './ExportPage.tsx'
import type { ExportRequest } from './options.ts'

const PDF_NAME = 'ada-example-cv-en.pdf'

const cabinetFile = (
  id: string,
  name: string,
  contentType: string,
): FileInfo => ({
  id,
  name,
  kind: 'OTHER',
  contentType,
  sizeBytes: 1024,
  uploadedAt: '2026-10-01T10:00:00Z',
  master: false,
})
const PORTRAIT = cabinetFile(
  '00000000-0000-0000-0000-00000000000a',
  'ada-example-portrait.jpg',
  'image/jpeg',
)
const CV_FILE = cabinetFile(
  '00000000-0000-0000-0000-00000000000c',
  PDF_NAME,
  'application/pdf',
)

/** Answers like the backend: HTML for the preview (German headings when asked), a small PDF for the download. */
const answerFor = (body: ExportRequest): Response =>
  body.format === 'HTML'
    ? new Response(
        `<!doctype html><html><body><h1>${body.language === 'DE' ? 'Lebenslauf' : 'Curriculum vitae'}</h1></body></html>`,
        { status: 200 },
      )
    : new Response('%PDF-1.4', {
        status: 200,
        headers: {
          'Content-Disposition': `attachment; filename="${PDF_NAME}"`,
        },
      })

/**
 * Records every export request body; `answer` decides the response (the default answers like the backend). The file
 * list (a GET without a body) answers with `cabinet`.
 */
function stubExport(
  answer: (body: ExportRequest) => Response = answerFor,
  cabinet: FileInfo[] = [],
) {
  const bodies: ExportRequest[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (init?.body === undefined && url === '/api/files')
        return new Response(JSON.stringify(cabinet), { status: 200 })
      const body = JSON.parse(String(init?.body)) as ExportRequest
      bodies.push(body)
      return answer(body)
    }),
  )
  return bodies
}

const failWith = (status: number, error: string) => () =>
  new Response(JSON.stringify({ error }), { status })

const htmlBodies = (bodies: ExportRequest[]) =>
  bodies.filter((b) => b.format === 'HTML')

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/export']}>
        <Routes>
          <Route path="/export" element={<ExportPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

const sectionList = () => screen.getByRole('group', { name: 'Sections' })
const sectionBox = (title: string) =>
  within(sectionList()).getByRole('checkbox', { name: title })
const optionList = () => screen.getByRole('group', { name: 'Options' })
const preview = () => screen.getByRole('region', { name: 'Preview' })
const downloadArea = () => screen.getByRole('region', { name: 'Download' })

let clicks: { download: string; href: string }[]

beforeEach(() => {
  ;(['log', 'info', 'warn', 'error', 'debug'] as const).forEach((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
  URL.createObjectURL = vi.fn(() => 'blob:cv-1')
  URL.revokeObjectURL = vi.fn()
  clicks = []
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicks.push({ download: this.download, href: this.href })
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('the export page', () => {
  it('starts as PDF, Classic, English, every section ticked, open source on, analysis off, work authorisation on', async () => {
    stubExport()
    mount()

    expect(screen.getByLabelText('Format')).toHaveValue('PDF')
    expect(screen.getByLabelText('Template')).toHaveValue('CLASSIC')
    expect(screen.getByLabelText('Language')).toHaveValue('EN')
    for (const title of [
      'Profile',
      'Experience',
      'Open source projects',
      'Education',
      'Skills',
      'Languages',
      'Publications',
    ]) {
      expect(sectionBox(title)).toBeChecked()
    }
    expect(
      within(optionList()).getByRole('checkbox', {
        name: 'Open source projects',
      }),
    ).toBeChecked()
    expect(
      within(optionList()).getByRole('checkbox', {
        name: 'Analysis summaries',
      }),
    ).not.toBeChecked()
    expect(
      within(optionList()).getByRole('checkbox', {
        name: 'Work authorisation',
      }),
    ).toBeChecked()
  })

  it('has one h1 and named regions for the preview and the download', () => {
    stubExport()
    mount()

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(
      screen.getByRole('heading', { level: 1, name: 'Export CV' }),
    ).toBeInTheDocument()
    expect(preview()).toBeInTheDocument()
    expect(downloadArea()).toBeInTheDocument()
  })

  it('shows the landing page with a sign-in button when signed out', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    mount(fakeAuth({ status: 'signedOut' }))
    expect(
      await screen.findByRole('button', { name: 'Sign in' }),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('sends the sections in the order chosen, without the unticked ones', async () => {
    const bodies = stubExport()
    const user = userEvent.setup()
    mount()

    await user.click(sectionBox('Publications'))
    // Skills is the fifth row; three moves up put it above Experience.
    for (let i = 0; i < 3; i++)
      await user.click(
        within(sectionList()).getByRole('button', {
          name: 'Move Skills up',
        }),
      )
    await user.click(screen.getByRole('button', { name: 'Download' }))

    await waitFor(() =>
      expect(bodies.some((b) => b.format === 'PDF')).toBe(true),
    )
    const pdf = bodies.find((b) => b.format === 'PDF')!
    expect(pdf.sections).toEqual([
      'PROFILE',
      'SKILLS',
      'EXPERIENCE',
      'OPEN_SOURCE',
      'EDUCATION',
      'LANGUAGES',
    ])
  })

  it('disables analysis summaries while open source projects are off', async () => {
    stubExport()
    const user = userEvent.setup()
    mount()

    const analysis = within(optionList()).getByRole('checkbox', {
      name: 'Analysis summaries',
    })
    await user.click(
      within(optionList()).getByRole('checkbox', {
        name: 'Open source projects',
      }),
    )
    expect(analysis).toBeDisabled()
  })
})

describe('the photo picker', () => {
  it('is hidden for the other templates and for Markdown', async () => {
    stubExport(answerFor, [PORTRAIT])
    const user = userEvent.setup()
    mount()

    expect(screen.queryByLabelText('Photo')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    expect(screen.getByLabelText('Photo')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Format'), 'MARKDOWN')
    expect(screen.queryByLabelText('Photo')).not.toBeInTheDocument()
  })

  it('lists the JPEG files by name with No photo selected', async () => {
    stubExport(answerFor, [CV_FILE, PORTRAIT])
    const user = userEvent.setup()
    mount()

    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    const select = await screen.findByLabelText('Photo')
    expect(select).toHaveValue('')
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['No photo', 'ada-example-portrait.jpg'])
  })

  it('says so when there are no JPEG files', async () => {
    stubExport(answerFor, [CV_FILE])
    const user = userEvent.setup()
    mount()

    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    expect(
      await screen.findByText(/There are no JPEG files in your file cabinet/),
    ).toBeInTheDocument()
  })

  it('sends the chosen photo id with the PDF', async () => {
    const bodies = stubExport(answerFor, [PORTRAIT])
    const user = userEvent.setup()
    mount()

    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    await user.selectOptions(await screen.findByLabelText('Photo'), PORTRAIT.id)
    await user.click(screen.getByRole('button', { name: 'Download' }))

    await waitFor(() => expect(clicks).toHaveLength(1))
    expect(bodies.find((b) => b.format === 'PDF')).toMatchObject({
      template: 'PROFESSIONAL',
      photoFileId: PORTRAIT.id,
    })
  })

  it('sends no photoFileId with No photo, and none for another template', async () => {
    const bodies = stubExport(answerFor, [PORTRAIT])
    const user = userEvent.setup()
    mount()

    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    await user.selectOptions(await screen.findByLabelText('Photo'), PORTRAIT.id)
    await user.selectOptions(screen.getByLabelText('Photo'), '')
    await user.click(screen.getByRole('button', { name: 'Download' }))
    await waitFor(() => expect(clicks).toHaveLength(1))
    expect(bodies.find((b) => b.format === 'PDF')).not.toHaveProperty(
      'photoFileId',
    )

    await user.selectOptions(screen.getByLabelText('Template'), 'CLASSIC')
    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    await user.selectOptions(await screen.findByLabelText('Photo'), PORTRAIT.id)
    await user.selectOptions(screen.getByLabelText('Template'), 'MODERN')
    await waitFor(() =>
      expect(htmlBodies(bodies).at(-1)?.template).toBe('MODERN'),
    )
    expect(htmlBodies(bodies).at(-1)).not.toHaveProperty('photoFileId')
  })

  it('shows the photo message without the file name', async () => {
    stubExport(
      (body) =>
        body.format === 'HTML'
          ? answerFor(body)
          : new Response(JSON.stringify({ error: 'photo_invalid' }), {
              status: 400,
            }),
      [PORTRAIT],
    )
    const user = userEvent.setup()
    mount()

    await user.selectOptions(screen.getByLabelText('Template'), 'PROFESSIONAL')
    await user.selectOptions(await screen.findByLabelText('Photo'), PORTRAIT.id)
    await user.click(screen.getByRole('button', { name: 'Download' }))

    const alert = await within(downloadArea()).findByRole('alert')
    expect(alert).toHaveTextContent(
      'The chosen photo cannot be used. Choose another JPEG file or "No photo".',
    )
    expect(alert.textContent).not.toContain('ada-example-portrait')
    expect(clicks).toHaveLength(0)
  })
})

describe('the live preview', () => {
  it('reloads with German headings when the language changes', async () => {
    stubExport()
    const user = userEvent.setup()
    mount()

    await waitFor(() =>
      expect(
        within(preview()).getByTitle('CV preview').getAttribute('srcdoc'),
      ).toContain('Curriculum vitae'),
    )
    await user.selectOptions(screen.getByLabelText('Language'), 'DE')
    await waitFor(() =>
      expect(
        within(preview()).getByTitle('CV preview').getAttribute('srcdoc'),
      ).toContain('Lebenslauf'),
    )
  })

  it('is an iframe with the sandbox attribute and no capabilities', async () => {
    stubExport()
    mount()
    const frame = within(preview()).getByTitle('CV preview')
    expect(frame.tagName).toBe('IFRAME')
    expect(frame).toHaveAttribute('sandbox', '')
  })

  it('sends one request for the final options after rapid changes', async () => {
    const bodies = stubExport()
    const user = userEvent.setup()
    mount()

    // The first change is the start of the burst: whatever was sent before it is counted.
    await user.click(
      within(optionList()).getByRole('checkbox', {
        name: 'Work authorisation',
      }),
    )
    const before = htmlBodies(bodies).length
    await user.selectOptions(screen.getByLabelText('Template'), 'MODERN')
    await user.selectOptions(screen.getByLabelText('Language'), 'DE')

    await waitFor(() =>
      expect(
        within(preview()).getByTitle('CV preview').getAttribute('srcdoc'),
      ).toContain('Lebenslauf'),
    )
    const sentAfter = htmlBodies(bodies).slice(before)
    expect(sentAfter).toHaveLength(1)
    expect(sentAfter[0]).toMatchObject({
      template: 'MODERN',
      language: 'DE',
      includeWorkAuthorisation: false,
    })
  })

  it('shows the name message with a link to the Contact section', async () => {
    stubExport(failWith(409, 'name_required'))
    mount()

    const alert = await within(preview()).findByRole('alert')
    expect(alert).toHaveTextContent(
      'Add your full name in the Contact section of your profile first.',
    )
    expect(
      within(alert).getByRole('link', { name: 'Go to the Contact section' }),
    ).toHaveAttribute('href', '/profile#contact')
  })
})

describe('the download', () => {
  it('posts PDF and saves the file under the server name', async () => {
    const bodies = stubExport()
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Download' }))

    await waitFor(() => expect(clicks).toHaveLength(1))
    expect(bodies.some((b) => b.format === 'PDF')).toBe(true)
    expect(clicks[0]!.download).toBe(PDF_NAME)
    expect(URL.createObjectURL).toHaveBeenCalledOnce()
  })

  it('revokes the object URL after the file is saved', async () => {
    stubExport()
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Download' }))
    await waitFor(() =>
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:cv-1'),
    )
  })

  it('shows the name message with a link and saves nothing', async () => {
    stubExport((body) =>
      body.format === 'HTML'
        ? answerFor(body)
        : new Response(JSON.stringify({ error: 'name_required' }), {
            status: 409,
          }),
    )
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Download' }))

    const alert = await within(downloadArea()).findByRole('alert')
    expect(
      within(alert).getByRole('link', { name: 'Go to the Contact section' }),
    ).toHaveAttribute('href', '/profile#contact')
    expect(clicks).toHaveLength(0)
  })

  it('shows the limit message and downloads nothing', async () => {
    stubExport((body) =>
      body.format === 'HTML'
        ? answerFor(body)
        : new Response(JSON.stringify({ error: 'too_many_exports' }), {
            status: 429,
          }),
    )
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Download' }))

    expect(await within(downloadArea()).findByRole('alert')).toHaveTextContent(
      'You have reached the limit of exports for this hour. Please try again later.',
    )
    expect(clicks).toHaveLength(0)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
})

describe('storage and logs', () => {
  it('keeps the CV out of browser storage and the console', async () => {
    stubExport()
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Download' }))
    await waitFor(() => expect(clicks).toHaveLength(1))
    await waitFor(() =>
      expect(
        within(preview()).getByTitle('CV preview').getAttribute('srcdoc'),
      ).toContain('Curriculum vitae'),
    )

    expect(setItem).not.toHaveBeenCalled()
    expect(document.cookie).toBe('')
    for (const m of ['log', 'info', 'warn', 'error', 'debug'] as const) {
      expect(console[m]).not.toHaveBeenCalled()
    }
  })
})
