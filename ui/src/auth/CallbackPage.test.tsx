import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeManager, fakeUser } from '../test/fakeManager.ts'
import { AuthProvider } from './AuthProvider.tsx'
import { CallbackPage } from './CallbackPage.tsx'
import { clearSignedInHint, hasSignedInHint, setSignedInHint } from './hint.ts'

function mount(manager: ReturnType<typeof fakeManager>['manager']) {
  window.history.pushState({}, '', '/callback?code=abc&state=xyz')
  return render(
    <AuthProvider manager={manager}>
      <MemoryRouter initialEntries={['/callback']}>
        <Routes>
          <Route path="/callback" element={<CallbackPage />} />
          <Route path="/" element={<p>home</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

beforeEach(() => {
  clearSignedInHint()
})

describe('CallbackPage', () => {
  it('completes the sign-in, sets the hint and goes home', async () => {
    const { manager, spies } = fakeManager()
    spies.signinCallback.mockResolvedValue(fakeUser())
    mount(manager)
    await waitFor(() => expect(screen.getByText('home')).toBeInTheDocument())
    expect(spies.signinCallback).toHaveBeenCalledTimes(1)
    expect(spies.signinCallback.mock.calls[0]).toEqual([
      expect.stringContaining('code=abc'),
    ])
    expect(hasSignedInHint()).toBe(true)
  })

  it('rejects a state mismatch without starting another sign-in', async () => {
    setSignedInHint()
    const { manager, spies } = fakeManager()
    spies.signinCallback.mockRejectedValue(
      new Error('No matching state found in storage'),
    )
    mount(manager)
    await waitFor(() =>
      expect(screen.getByText('Sign-in failed')).toBeInTheDocument(),
    )
    expect(
      screen.getByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument()
    expect(spies.signinRedirect).not.toHaveBeenCalled()
    expect(hasSignedInHint()).toBe(false)
  })

  it('shows a fixed message when the auth service refuses, without its error details', async () => {
    const { manager, spies } = fakeManager()
    spies.signinCallback.mockRejectedValue(
      Object.assign(new Error('access_denied'), {
        error: 'access_denied',
        error_description: 'secret detail',
      }),
    )
    mount(manager)
    await waitFor(() =>
      expect(screen.getByText('Sign-in failed')).toBeInTheDocument(),
    )
    expect(document.body).not.toHaveTextContent('access_denied')
    expect(document.body).not.toHaveTextContent('secret detail')
  })
})
