import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import { safeHttpUrl } from '../postings/links.ts'
import { loadMatches } from './matchesApi.ts'
import { MATCH_PAGE_SIZE, type MatchItem } from './types.ts'

/** The signed-in user's prefilter matches. A visitor without a session sees the landing page. */
export function MatchesPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/matches" />
    case 'error':
      return (
        <div className="page page--narrow">
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </div>
      )
    case 'signedIn':
      return <MatchesList />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; matches: MatchItem[] }

function MatchesList() {
  const auth = useAuth()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [load, setLoad] = useState<Load>({ state: 'loading' })

  const fetchPage = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadMatches(auth, page)
    if (result.kind === 'loaded')
      setLoad({ state: 'ready', matches: result.matches })
    else if (result.kind === 'failed')
      setLoad({ state: 'failed', message: result.message })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; loading belongs to the page and the retry button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  useEffect(() => {
    void fetchPage()
  }, [fetchPage])

  const goTo = (next: number) =>
    setParams(next > 0 ? { page: String(next) } : {})
  const matches = load.state === 'ready' ? load.matches : []

  return (
    <div className="page">
      <h1>Matches</h1>
      <p>
        <Link to="/">Back</Link>
      </p>
      {load.state === 'loading' && <p role="status">Loading matches…</p>}
      {load.state === 'failed' && (
        <div role="alert">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchPage()}>
            Try again
          </button>
        </div>
      )}
      {load.state === 'ready' && matches.length === 0 && (
        <p>
          No matches yet. <Link to="/profile">Save your profile first</Link>,
          then check back after the next crawl.
        </p>
      )}
      {load.state === 'ready' && matches.length > 0 && (
        <ul className="postings">
          {matches.map((m) => {
            const href = safeHttpUrl(m.url)
            return (
              <li key={m.postingId}>
                <h2>{m.title}</h2>
                <p>
                  {m.company}
                  {m.city ? ` · ${m.city}` : ''}
                  {m.canton ? ` (${m.canton})` : ''} · score {m.score}
                </p>
                {m.reasons.length > 0 && (
                  <ul className="reasons">
                    {m.reasons.map((r) => (
                      <li key={r}>{r}</li>
                    ))}
                  </ul>
                )}
                {href && (
                  <ul className="original-links">
                    <li>
                      <a href={href} target="_blank" rel="noopener noreferrer">
                        Open original
                      </a>
                    </li>
                  </ul>
                )}
              </li>
            )
          })}
        </ul>
      )}
      <div className="actions">
        <button
          type="button"
          className="secondary"
          disabled={page === 0}
          onClick={() => goTo(page - 1)}
        >
          Previous
        </button>
        <span>Page {page + 1}</span>
        <button
          type="button"
          className="secondary"
          disabled={load.state !== 'ready' || matches.length < MATCH_PAGE_SIZE}
          onClick={() => goTo(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}
