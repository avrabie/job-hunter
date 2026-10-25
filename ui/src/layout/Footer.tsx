import { useCallback, useEffect, useState } from 'react'
import { apiFetch, ApiError } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { useAuth } from '../auth/authContext.ts'

type ApiStatus = { state: 'checking' } | { state: 'done'; text: string }

/** One call to `/api/` per signed-in visit that proves the whole path works, with a way to ask again. */
function ServiceStatus() {
  const auth = useAuth()
  const [status, setStatus] = useState<ApiStatus>({ state: 'checking' })

  const check = useCallback(async () => {
    setStatus({ state: 'checking' })
    try {
      const res = await apiFetch(auth, '/api/')
      setStatus({
        state: 'done',
        text: res.ok ? 'The backend answered.' : messageForStatus(res.status),
      })
    } catch (e) {
      // After a final 401 the tokens are already discarded and the app shows the landing page.
      if (e instanceof ApiError && e.kind === 'unauthorized') return
      setStatus({ state: 'done', text: messageForError(e) })
    }
    // `auth` changes identity when the state does; the check must run once per mount and on retry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void check()
  }, [check])

  return (
    <p className="service-status">
      <span className="service-status__label">Service status:</span>{' '}
      <span role="status">
        {status.state === 'checking' ? 'Checking the backend…' : status.text}
      </span>{' '}
      <button
        type="button"
        className="button--link"
        onClick={() => void check()}
      >
        Check again
      </button>
    </p>
  )
}

/** The page footer. Only signed-in pages check the service: the call needs a token. */
export function Footer({ signedIn }: { signedIn: boolean }) {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <p className="site-footer__note">
          Job Hunter. You review everything; nothing is sent to employers.
        </p>
        {signedIn && <ServiceStatus />}
      </div>
    </footer>
  )
}
