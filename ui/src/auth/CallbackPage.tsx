import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { PageHeader } from '../ui/PageHeader.tsx'
import '../styles/account.css'
import { useAuth } from './authContext.ts'

/**
 * Finishes the sign-in at `/callback`. Failures show one fixed message: the address can carry error codes and
 * descriptions from the auth service, and none of that is shown or logged.
 */
export function CallbackPage() {
  const { completeSignIn, signIn } = useAuth()
  const navigate = useNavigate()
  const [failed, setFailed] = useState(false)
  // The code can be exchanged once; React's development double render must not try twice.
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    completeSignIn(window.location.href).then(
      // Replacing the entry also removes the code and state from the address bar and history.
      ({ returnTo }) => navigate(returnTo, { replace: true }),
      () => setFailed(true),
    )
  }, [completeSignIn, navigate])

  if (!failed)
    return (
      <div className="page page--narrow">
        <p role="status">Signing you in…</p>
      </div>
    )
  return (
    <div className="page page--narrow">
      <PageHeader
        title="Sign-in failed"
        lead="We could not complete the sign-in."
      />
      <div className="account-actions">
        <button type="button" onClick={() => void signIn()}>
          Try again
        </button>
      </div>
    </div>
  )
}
