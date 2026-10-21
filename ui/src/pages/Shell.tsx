import { Link } from 'react-router'
import { useCallback, useEffect, useState } from 'react'
import { apiFetch, ApiError } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { useAuth } from '../auth/authContext.ts'

type ApiStatus = { state: 'checking' } | { state: 'done'; text: string }

/** The signed-in page: who is signed in, sign out, and one call to `/api/` that proves the whole path works. */
export function Shell({ identity }: { identity: string }) {
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
    <main>
      <h1>Job Hunter</h1>
      <p>
        Signed in as <strong>{identity}</strong>
      </p>
      <p role="status">
        {status.state === 'checking' ? 'Checking the backend…' : status.text}
      </p>
      <div className="actions">
        <Link className="button" to="/profile">
          Your profile
        </Link>
        <button
          type="button"
          className="secondary"
          onClick={() => void check()}
        >
          Check again
        </button>
        <button type="button" onClick={() => void auth.signOut()}>
          Sign out
        </button>
      </div>
    </main>
  )
}
