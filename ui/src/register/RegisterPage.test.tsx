import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { RegisterPage } from './RegisterPage.tsx'

const signIn = vi.fn(async () => {})
const auth = { state: { status: 'signedOut' }, signIn } as unknown as AuthApi

function stubFetch(respond: () => Response | Promise<Response>) {
  const fn = vi.fn(async () => respond())
  vi.stubGlobal('fetch', fn)
  return fn
}

async function fillAndSubmit() {
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  await userEvent.type(screen.getByLabelText('Email'), 'ada@example.ch')
  await userEvent.type(
    screen.getByLabelText('Password'),
    'CorrectHorse-Battery9',
  )
  await userEvent.type(screen.getByLabelText('First name (optional)'), 'Ada')
  await userEvent.click(screen.getByRole('button', { name: 'Register' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  signIn.mockClear()
})

describe('RegisterPage', () => {
  it('posts the form and starts the sign-in after a 201', async () => {
    const fetchSpy = stubFetch(
      () => new Response('{"email":"ada@example.ch"}', { status: 201 }),
    )
    await fillAndSubmit()
    await waitFor(() => expect(signIn).toHaveBeenCalledTimes(1))
    const [url, init] = fetchSpy.mock.calls[0] as unknown as [
      string,
      RequestInit,
    ]
    expect(url).toBe('/auth/api/register')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      email: 'ada@example.ch',
      password: 'CorrectHorse-Battery9',
      firstName: 'Ada',
    })
  })

  it('says the email is taken on 409 and keeps the other values', async () => {
    stubFetch(
      () =>
        new Response('{"error":"duplicate_email","fields":{}}', {
          status: 409,
        }),
    )
    await fillAndSubmit()
    expect(await screen.findByText(/already registered/)).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.ch')
    expect(screen.getByLabelText('First name (optional)')).toHaveValue('Ada')
    expect(signIn).not.toHaveBeenCalled()
  })

  it('shows each field error next to its field on 400', async () => {
    stubFetch(
      () =>
        new Response(
          JSON.stringify({
            error: 'validation_failed',
            fields: {
              password: 'must be 8 to 72 characters',
              email: 'must be a well-formed email address',
            },
          }),
          { status: 400 },
        ),
    )
    await fillAndSubmit()
    expect(
      await screen.findByText('must be 8 to 72 characters'),
    ).toBeInTheDocument()
    // The message is linked to its field and the field is marked invalid, without becoming part of the name.
    const password = screen.getByLabelText(/^Password/)
    expect(password).toHaveAccessibleDescription('must be 8 to 72 characters')
    expect(password).toHaveAttribute('aria-invalid', 'true')
    expect(password).toHaveAccessibleName('Password')
    const email = screen.getByLabelText(/^Email/)
    expect(email).toHaveAccessibleDescription(
      'must be a well-formed email address',
    )
    expect(email).toHaveAccessibleName('Email')
    expect(signIn).not.toHaveBeenCalled()
  })

  it('says registration is unavailable on a network failure and allows another try', async () => {
    stubFetch(() => Promise.reject(new TypeError('network')))
    await fillAndSubmit()
    expect(await screen.findByText(/unavailable/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Register' })).toBeEnabled()
  })
})
