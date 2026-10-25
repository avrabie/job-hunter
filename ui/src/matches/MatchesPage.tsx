import { useCallback, useEffect, useState } from 'react'
import { PageLoading } from '../ui/PageLoading.tsx'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import { safeHttpUrl } from '../postings/links.ts'
import { Alert } from '../ui/Alert.tsx'
import { Badge } from '../ui/Badge.tsx'
import { Card } from '../ui/Card.tsx'
import { EmptyState } from '../ui/EmptyState.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { loadMatches } from './matchesApi.ts'
import { MATCH_PAGE_SIZE, type MatchItem } from './types.ts'
import '../styles/matches.css'

/** The signed-in user's prefilter matches. A visitor without a session sees the landing page. */
export function MatchesPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <PageLoading title="Matches" />
    case 'signedOut':
      return <Landing returnTo="/matches" />
    case 'error':
      return (
        <div className="page page--narrow">
          <PageHeader
            title="Sign-in failed"
            lead="We could not reach the sign-in service."
          />
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
      <PageHeader
        title="Matches"
        lead="Postings ranked against your profile, with the reasons."
        actions={
          <Link className="button button--secondary" to="/">
            Back
          </Link>
        }
      />
      {load.state === 'loading' && (
        <p role="status" className="matches-status">
          Loading matches…
        </p>
      )}
      {load.state === 'failed' && (
        <Alert tone="danger">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchPage()}>
            Try again
          </button>
        </Alert>
      )}
      {load.state === 'ready' && matches.length === 0 && (
        <EmptyState title="No matches yet.">
          <Link to="/profile">Save your profile first</Link>, then check back
          after the next crawl.
        </EmptyState>
      )}
      {load.state === 'ready' && matches.length > 0 && (
        <ul className="match-list">
          {matches.map((m) => {
            const href = safeHttpUrl(m.url)
            return (
              <li key={m.postingId}>
                <Card
                  title={m.title}
                  actions={
                    href ? (
                      <a
                        className="button button--secondary"
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Open original
                      </a>
                    ) : undefined
                  }
                >
                  <p className="match-card__meta">
                    {m.company}
                    {m.city ? ` · ${m.city}` : ''}
                    {m.canton ? ` (${m.canton})` : ''} · score {m.score}
                  </p>
                  {m.reasons.length > 0 && (
                    <ul className="match-reasons" aria-label="Reasons">
                      {m.reasons.map((r) => (
                        <li key={r}>
                          <Badge>{r}</Badge>
                        </li>
                      ))}
                    </ul>
                  )}
                </Card>
              </li>
            )
          })}
        </ul>
      )}
      <nav aria-label="Pages" className="match-pagination">
        <button
          type="button"
          className="button--secondary"
          disabled={page === 0}
          onClick={() => goTo(page - 1)}
        >
          Previous
        </button>
        <span>Page {page + 1}</span>
        <button
          type="button"
          className="button--secondary"
          disabled={load.state !== 'ready' || matches.length < MATCH_PAGE_SIZE}
          onClick={() => goTo(page + 1)}
        >
          Next
        </button>
      </nav>
    </div>
  )
}
