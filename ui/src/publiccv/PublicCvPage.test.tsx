import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { PublicCvPage } from './PublicCvPage.tsx'
import { HANDLE_FORM_MESSAGE } from './options.ts'

interface Call {
  method: string
  url: string
  body: Record<string, unknown> | undefined
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

const ORIGIN = window.location.origin
const address = (handle: string) => `${ORIGIN}/cv/${handle}`

/** A saved page as the server answers it; the fixture is synthetic. */
const savedState = (over: Record<string, unknown> = {}) => ({
  configured: true,
  handle: 'ada-example',
  published: true,
  locked: false,
  settings: {
    template: 'CLASSIC',
    language: 'EN',
    sections: [],
    headline: false,
    analysis: false,
    workAuthorisation: false,
    location: false,
    contact: {
      email: false,
      phone: false,
      website: false,
      linkedin: false,
      codeAccounts: false,
    },
    indexable: false,
  },
  handleChangeableAt: null,
  updatedAt: '2026-10-01T10:00:00Z',
  ...over,
})

/** Answers like the backend for the calls this page makes. */
function defaultAnswer(call: Call): Response {
  if (call.method === 'GET' && call.url === '/api/cv/public')
    return json(200, { configured: false })
  if (call.url.startsWith('/api/cv/public/handle'))
    return json(200, { available: true })
  if (call.method === 'PUT')
    return json(200, savedState({ ...call.body, locked: false }))
  if (call.method === 'DELETE') return new Response(null, { status: 204 })
  if (call.url === '/api/cv/public/preview')
    return json(200, { path: '/api/cv/public/preview/ticket-1' })
  return json(404, { error: 'not_found' })
}

/** Records every request (method, URL, JSON body) and answers with `answer`. */
function stubApi(answer: (call: Call) => Response = defaultAnswer) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const call: Call = {
        method: init?.method ?? 'GET',
        url,
        body: init?.body
          ? (JSON.parse(String(init.body)) as Record<string, unknown>)
          : undefined,
      }
      calls.push(call)
      return answer(call)
    }),
  )
  return calls
}

const handleCalls = (calls: Call[]) =>
  calls.filter((c) => c.url.startsWith('/api/cv/public/handle'))
const putCalls = (calls: Call[]) => calls.filter((c) => c.method === 'PUT')

function mount(auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/publish']}>
        <Routes>
          <Route path="/publish" element={<PublicCvPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

/** Lets timers and pending requests run, as the user would wait. */
async function wait(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

const field = () => screen.getByLabelText('Handle')
const sectionBox = (title: string) =>
  screen.getByRole('checkbox', { name: title })
const button = (name: string) => screen.getByRole('button', { name })
const sectionRows = () => screen.getByRole('list')
const typeHandle = (value: string) =>
  fireEvent.change(field(), { target: { value } })

/** Loads the page, types a handle, waits out the availability check and ticks the given sections. */
async function prepareToPublish(sections: string[] = []) {
  typeHandle('ada-example')
  await wait(400)
  for (const title of sections) fireEvent.click(sectionBox(title))
}

let clipboard: unknown

beforeEach(() => {
  vi.useFakeTimers()
  ;(['log', 'info', 'warn', 'error', 'debug'] as const).forEach((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
  clipboard = Reflect.get(navigator, 'clipboard')
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  Reflect.deleteProperty(navigator, 'clipboard')
  if (clipboard !== undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: clipboard,
      configurable: true,
    })
})

/** A cabinet entry as the files list answers it; synthetic. */
const cabinetFile = (id: string, name: string, contentType: string) => ({
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
  'ada-example-cv.pdf',
  'application/pdf',
)

/** Like `defaultAnswer`, and the file list answers with `cabinet`. */
const withCabinet =
  (cabinet: unknown[], answer: (call: Call) => Response = defaultAnswer) =>
  (call: Call): Response =>
    call.method === 'GET' && call.url === '/api/files'
      ? json(200, cabinet)
      : answer(call)

/** Picks the Professional template, which shows the photo picker. */
async function chooseProfessional() {
  fireEvent.change(screen.getByLabelText('Template'), {
    target: { value: 'PROFESSIONAL' },
  })
  await wait()
}

describe('the photo picker', () => {
  it('is hidden for the other templates and shown for Professional', async () => {
    stubApi(withCabinet([PORTRAIT]))
    mount()
    await wait()

    expect(screen.queryByLabelText('Photo')).toBeNull()
    await chooseProfessional()
    expect(screen.getByLabelText('Photo')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Template'), {
      target: { value: 'MODERN' },
    })
    await wait()
    expect(screen.queryByLabelText('Photo')).toBeNull()
  })

  it('lists the JPEG files by name with No photo selected and says the photo is public', async () => {
    stubApi(withCabinet([CV_FILE, PORTRAIT]))
    mount()
    await wait()
    await chooseProfessional()

    const select = screen.getByLabelText('Photo')
    expect(select).toHaveValue('')
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['No photo', 'ada-example-portrait.jpg'])
    expect(
      screen.getByText(
        'The photo becomes visible to everyone with your link once you publish.',
      ),
    ).toBeInTheDocument()
  })

  it('sends the chosen photo id with the settings on publish', async () => {
    const calls = stubApi(withCabinet([PORTRAIT]))
    mount()
    await wait()
    await prepareToPublish()
    await chooseProfessional()
    fireEvent.change(screen.getByLabelText('Photo'), {
      target: { value: PORTRAIT.id },
    })

    fireEvent.click(button('Publish'))
    await wait()

    expect(putCalls(calls)[0]!.body).toMatchObject({
      settings: expect.objectContaining({
        template: 'PROFESSIONAL',
        photoFileId: PORTRAIT.id,
      }),
    })
  })

  it('sends a null photo id with No photo', async () => {
    const calls = stubApi(withCabinet([PORTRAIT]))
    mount()
    await wait()
    await prepareToPublish()
    await chooseProfessional()

    fireEvent.click(button('Publish'))
    await wait()

    expect(putCalls(calls)[0]!.body).toMatchObject({
      settings: expect.objectContaining({ photoFileId: null }),
    })
  })

  it('shows the photo message without the file name when the save is refused', async () => {
    stubApi(
      withCabinet([PORTRAIT], (call) =>
        call.method === 'PUT'
          ? json(400, { error: 'photo_invalid' })
          : defaultAnswer(call),
      ),
    )
    mount()
    await wait()
    await prepareToPublish()
    await chooseProfessional()
    fireEvent.change(screen.getByLabelText('Photo'), {
      target: { value: PORTRAIT.id },
    })

    fireEvent.click(button('Publish'))
    await wait()

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent(
      'The chosen photo cannot be used. Choose another JPEG file or "No photo".',
    )
    expect(alert.textContent).not.toContain('ada-example-portrait')
  })
})

describe('the public CV page', () => {
  it('starts private: every checkbox unticked, Classic, English, and the name is always shown', async () => {
    stubApi()
    mount()
    await wait()

    const boxes = screen.getAllByRole('checkbox')
    expect(boxes).toHaveLength(17)
    for (const box of boxes) expect(box).not.toBeChecked()
    expect(screen.getByLabelText('Template')).toHaveValue('CLASSIC')
    expect(screen.getByLabelText('Language')).toHaveValue('EN')
    expect(
      screen.getByText(/Your full name is always shown/),
    ).toHaveTextContent('Preferences are never shown')
    expect(screen.queryByRole('button', { name: /Move .* up/ })).toBeNull()
    expect(screen.getByText('Your CV is not public.')).toBeInTheDocument()
  })

  it('loads the saved state: handle, settings and the published state as saved', async () => {
    const calls = stubApi((call) =>
      call.method === 'GET'
        ? json(
            200,
            savedState({
              settings: {
                ...savedState().settings,
                template: 'MODERN',
                sections: ['SKILLS', 'EXPERIENCE'],
                headline: true,
                contact: { ...savedState().settings.contact, linkedin: true },
              },
            }),
          )
        : defaultAnswer(call),
    )
    mount()
    await wait()

    expect(field()).toHaveValue('ada-example')
    expect(screen.getByLabelText('Template')).toHaveValue('MODERN')
    expect(sectionBox('Skills')).toBeChecked()
    expect(sectionBox('Experience')).toBeChecked()
    expect(sectionBox('Profile')).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Headline' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'LinkedIn' })).toBeChecked()
    expect(screen.getByText(/Your CV is live at/)).toHaveTextContent(
      address('ada-example'),
    )
    // The owner's own handle needs no availability request.
    await wait(400)
    expect(handleCalls(calls)).toHaveLength(0)
  })

  it('shows the landing page to a visitor without a session and calls nothing', async () => {
    const calls = stubApi()
    mount(fakeAuth({ status: 'signedOut' }))
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('shows a failed load with its fixed message and no form', async () => {
    stubApi((call) =>
      call.method === 'GET'
        ? json(500, { error: 'Ada Example stack trace' })
        : defaultAnswer(call),
    )
    mount()
    await wait()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByLabelText('Handle')).toBeNull()
  })

  it('checks the availability once after the user pauses for 400 ms and shows the address', async () => {
    const calls = stubApi()
    mount()
    await wait()

    typeHandle('ada-example')
    await wait(399)
    expect(handleCalls(calls)).toHaveLength(0)
    await wait(1)

    expect(handleCalls(calls)).toHaveLength(1)
    expect(handleCalls(calls)[0]!.url).toBe(
      '/api/cv/public/handle?handle=ada-example',
    )
    expect(screen.getByText('Available.')).toBeInTheDocument()
    expect(screen.getByText(address('ada-example'))).toBeInTheDocument()
  })

  it('sends one availability request for the final value when the user types quickly', async () => {
    const calls = stubApi()
    mount()
    await wait()

    for (const value of ['a', 'ad', 'ada-', 'ada-e', 'ada-ex']) {
      typeHandle(value)
      await wait(100)
    }
    await wait(400)

    expect(handleCalls(calls)).toHaveLength(1)
    expect(handleCalls(calls)[0]!.url).toBe(
      '/api/cv/public/handle?handle=ada-ex',
    )
  })

  it('lower-cases the handle and sends no request for a form that is wrong', async () => {
    const calls = stubApi()
    mount()
    await wait()

    typeHandle('Ad')
    expect(field()).toHaveValue('ad')
    await wait(400)

    expect(screen.getByText(HANDLE_FORM_MESSAGE)).toHaveTextContent(
      '3 to 30 characters',
    )
    expect(handleCalls(calls)).toHaveLength(0)
  })

  it('shows the reason when the handle is taken', async () => {
    stubApi((call) =>
      call.url.startsWith('/api/cv/public/handle')
        ? json(200, { available: false, reason: 'taken' })
        : defaultAnswer(call),
    )
    mount()
    await wait()
    typeHandle('ada-example')
    await wait(400)
    expect(
      screen.getByText('This handle is already taken.'),
    ).toBeInTheDocument()
  })

  it('disables the handle field and shows the date when the handle can be changed later', async () => {
    stubApi((call) =>
      call.method === 'GET'
        ? json(
            200,
            savedState({
              published: false,
              handleChangeableAt: '2099-01-01T10:00:00Z',
            }),
          )
        : defaultAnswer(call),
    )
    mount()
    await wait()

    expect(field()).toBeDisabled()
    expect(
      screen.getByText(/You can change the handle from/),
    ).toHaveTextContent('1 January 2099')
  })

  it('publishes: saves the handle, the ticked section and published true, then shows the live banner', async () => {
    const calls = stubApi()
    mount()
    await wait()
    await prepareToPublish(['Experience'])

    fireEvent.click(button('Publish'))
    await wait()

    expect(putCalls(calls)).toHaveLength(1)
    expect(putCalls(calls)[0]!.body).toEqual({
      handle: 'ada-example',
      published: true,
      settings: expect.objectContaining({ sections: ['EXPERIENCE'] }),
    })
    expect(screen.getByText(/Your CV is live at/)).toHaveTextContent(
      address('ada-example'),
    )
    expect(
      screen.getByText(/Every change you save in your profile/),
    ).toBeInTheDocument()
  })

  it('sends the sections in the order the owner moved them', async () => {
    const calls = stubApi()
    mount()
    await wait()
    await prepareToPublish(['Experience', 'Skills'])

    fireEvent.click(button('Move Skills up'))
    fireEvent.click(button('Publish'))
    await wait()

    expect(putCalls(calls)[0]!.body!.settings).toMatchObject({
      sections: ['SKILLS', 'EXPERIENCE'],
    })
    const rows = within(sectionRows()).getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('Skills')
    expect(rows[1]).toHaveTextContent('Experience')
  })

  it('pauses: sends published false and the banner says the page is not public', async () => {
    const calls = stubApi((call) =>
      call.method === 'GET' ? json(200, savedState()) : defaultAnswer(call),
    )
    mount()
    await wait()

    fireEvent.click(button('Pause'))
    await wait()

    expect(putCalls(calls)[0]!.body).toMatchObject({ published: false })
    expect(screen.getByText('Your CV is not public.')).toBeInTheDocument()
  })

  it('saves settings on a live page without changing whether it is live', async () => {
    const calls = stubApi((call) =>
      call.method === 'GET' ? json(200, savedState()) : defaultAnswer(call),
    )
    mount()
    await wait()

    fireEvent.click(sectionBox('Education'))
    fireEvent.click(button('Save'))
    await wait()

    expect(putCalls(calls)[0]!.body).toMatchObject({
      handle: 'ada-example',
      published: true,
      settings: expect.objectContaining({ sections: ['EDUCATION'] }),
    })
  })

  it('asks for confirmation before removing and sends nothing when declined', async () => {
    const calls = stubApi((call) =>
      call.method === 'GET' ? json(200, savedState()) : defaultAnswer(call),
    )
    mount()
    await wait()

    fireEvent.click(button('Remove'))
    expect(
      screen.getByRole('heading', { name: 'Remove the public CV?' }),
    ).toBeInTheDocument()
    fireEvent.click(button('Keep it'))
    await wait()

    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
  })

  it('removes after confirmation and returns the page to its default state', async () => {
    const calls = stubApi((call) =>
      call.method === 'GET' ? json(200, savedState()) : defaultAnswer(call),
    )
    mount()
    await wait()

    fireEvent.click(button('Remove'))
    fireEvent.click(button('Remove the public CV'))
    await wait()

    expect(calls.filter((c) => c.method === 'DELETE')).toHaveLength(1)
    expect(field()).toHaveValue('')
    expect(screen.getByText('Your CV is not public.')).toBeInTheDocument()
    for (const box of screen.getAllByRole('checkbox')) {
      expect(box).not.toBeChecked()
    }
  })

  it('tells the user to add the full name and links to the Contact section', async () => {
    stubApi((call) =>
      call.method === 'PUT'
        ? json(409, { error: 'name_required' })
        : defaultAnswer(call),
    )
    mount()
    await wait()
    await prepareToPublish()

    fireEvent.click(button('Publish'))
    await wait()

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Add your full name in the Contact section of your profile first.',
    )
    expect(
      screen.getByRole('link', { name: 'Go to the Contact section' }),
    ).toHaveAttribute('href', '/profile#contact')
  })

  it('shows a taken handle at the handle field and keeps the other edits', async () => {
    stubApi((call) =>
      call.method === 'PUT'
        ? json(409, { error: 'handle_taken' })
        : defaultAnswer(call),
    )
    mount()
    await wait()
    await prepareToPublish(['Experience'])

    fireEvent.click(button('Publish'))
    await wait()

    expect(field()).toHaveAccessibleDescription(
      'This handle is already taken. Choose another one.',
    )
    expect(sectionBox('Experience')).toBeChecked()
  })

  it('does not send a publish for a handle that is not well formed', async () => {
    const calls = stubApi()
    mount()
    await wait()

    typeHandle('Ad')
    fireEvent.click(button('Publish'))
    await wait()

    expect(putCalls(calls)).toHaveLength(0)
    expect(field()).toHaveAccessibleDescription(HANDLE_FORM_MESSAGE)
  })

  it('says an administrator disabled the page and disables Publish when the page is locked', async () => {
    stubApi((call) =>
      call.method === 'GET'
        ? json(200, savedState({ published: false, locked: true }))
        : defaultAnswer(call),
    )
    mount()
    await wait()

    expect(
      screen.getByText(/An administrator disabled this page/),
    ).toBeInTheDocument()
    expect(button('Publish')).toBeDisabled()
  })

  it('copies the address of the live page and confirms it', async () => {
    const writeText = vi.fn(async (_text: string) => {})
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    stubApi((call) =>
      call.method === 'GET' ? json(200, savedState()) : defaultAnswer(call),
    )
    mount()
    await wait()

    fireEvent.click(button('Copy link'))
    await wait()

    expect(writeText).toHaveBeenCalledWith(address('ada-example'))
    expect(button('Copied')).toBeInTheDocument()
  })

  it('opens the preview in a new tab with the settings on screen, and renders nothing in the page', async () => {
    const calls = stubApi()
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    const { container } = mount()
    await wait()

    fireEvent.click(sectionBox('Experience'))
    fireEvent.click(button('Preview'))
    await wait()

    const preview = calls.find((c) => c.url === '/api/cv/public/preview')!
    expect(preview.method).toBe('POST')
    expect(preview.body).toEqual({
      settings: expect.objectContaining({ sections: ['EXPERIENCE'] }),
    })
    expect(open).toHaveBeenCalledWith(
      '/api/cv/public/preview/ticket-1',
      '_blank',
      'noopener',
    )
    expect(container.querySelector('iframe')).toBeNull()
    expect(container.querySelector('[srcdoc]')).toBeNull()
  })

  it('says the preview is made from the saved profile and the settings on screen', async () => {
    stubApi()
    mount()
    await wait()
    expect(
      screen.getByText(
        /made from your saved profile and the settings on this page/,
      ),
    ).toBeInTheDocument()
  })

  it('shows a fixed message and opens no tab when the preview is refused', async () => {
    stubApi((call) =>
      call.url === '/api/cv/public/preview'
        ? json(429, { error: 'rate_limited', detail: 'Ada Example' })
        : defaultAnswer(call),
    )
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    mount()
    await wait()

    fireEvent.click(button('Preview'))
    await wait()

    expect(open).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'You have reached the limit for this hour. Please try again later.',
    )
    expect(screen.queryByText(/Ada Example/)).toBeNull()
  })
})
