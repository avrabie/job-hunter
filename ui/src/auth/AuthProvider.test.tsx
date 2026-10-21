import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeManager, fakeUser } from '../test/fakeManager.ts'
import { AuthProvider } from './AuthProvider.tsx'
import { useAuth } from './authContext.ts'
import { clearSignedInHint, hasSignedInHint, setSignedInHint } from './hint.ts'

function Probe() {
  const auth = useAuth()
  return (
    <div>
      <p data-testid="status">{auth.state.status}</p>
      <button onClick={() => void auth.signOut()}>out</button>
      <button onClick={() => void auth.renew()}>renew</button>
    </div>
  )
}

function mount(manager: ReturnType<typeof fakeManager>['manager']) {
  return render(
    <AuthProvider manager={manager}>
      <Probe />
    </AuthProvider>,
  )
}

beforeEach(() => {
  clearSignedInHint()
  window.history.pushState({}, '', '/')
})

describe('start-up restore', () => {
  it('shows signed out without redirecting when there is no hint', async () => {
    const { manager, spies } = fakeManager()
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedOut'),
    )
    expect(spies.signinRedirect).not.toHaveBeenCalled()
  })

  it('redirects once to sign in when the hint is set and there are no tokens', async () => {
    setSignedInHint()
    const { manager, spies } = fakeManager()
    mount(manager)
    await waitFor(() => expect(spies.signinRedirect).toHaveBeenCalledTimes(1))
    expect(screen.getByTestId('status')).toHaveTextContent('loading')
  })

  it('asks to come back to the page that was open before the reload', async () => {
    setSignedInHint()
    window.history.pushState({}, '', '/profile')
    const { manager, spies } = fakeManager()
    mount(manager)
    await waitFor(() => expect(spies.signinRedirect).toHaveBeenCalledTimes(1))
    expect(spies.signinRedirect).toHaveBeenCalledWith({
      prompt: 'none',
      state: { returnTo: '/profile' },
    })
  })

  it('does not take an unknown address as the page to return to', async () => {
    setSignedInHint()
    window.history.pushState({}, '', '/some/other/page')
    const { manager, spies } = fakeManager()
    mount(manager)
    await waitFor(() => expect(spies.signinRedirect).toHaveBeenCalledTimes(1))
    expect(spies.signinRedirect).toHaveBeenCalledWith({
      prompt: 'none',
      state: { returnTo: '/' },
    })
  })

  it('does not redirect on the callback address', async () => {
    setSignedInHint()
    window.history.pushState({}, '', '/callback?code=x&state=y')
    const { manager, spies } = fakeManager()
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedOut'),
    )
    expect(spies.signinRedirect).not.toHaveBeenCalled()
  })

  it('is signed in when the tokens are still in memory', async () => {
    const { manager, spies } = fakeManager(fakeUser())
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedIn'),
    )
    expect(spies.signinRedirect).not.toHaveBeenCalled()
  })

  it('shows an error and clears the hint when the redirect cannot start', async () => {
    setSignedInHint()
    const { manager, spies } = fakeManager()
    spies.signinRedirect.mockRejectedValue(new Error('network'))
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('error'),
    )
    expect(hasSignedInHint()).toBe(false)
  })
})

describe('sign-out', () => {
  it('clears the hint and ends the session at the auth service', async () => {
    setSignedInHint()
    const { manager, spies } = fakeManager(fakeUser())
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedIn'),
    )
    await userEvent.click(screen.getByText('out'))
    expect(spies.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(hasSignedInHint()).toBe(false)
  })
})

describe('renewal', () => {
  it('discards the user and the hint when a renewal fails', async () => {
    setSignedInHint()
    const { manager, spies, fireRenewError } = fakeManager(fakeUser())
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedIn'),
    )
    act(() => fireRenewError())
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedOut'),
    )
    expect(spies.removeUser).toHaveBeenCalled()
    expect(hasSignedInHint()).toBe(false)
  })

  it('takes the new tokens when a renewal succeeds', async () => {
    const { manager, spies } = fakeManager(fakeUser())
    spies.signinSilent.mockResolvedValue(fakeUser({ access: 'access-2' }))
    mount(manager)
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('signedIn'),
    )
    await userEvent.click(screen.getByText('renew'))
    expect(spies.signinSilent).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('status')).toHaveTextContent('signedIn')
  })
})
