import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { InterestedButton } from '../applications/InterestedButton.tsx'
import {
  trackedOf,
  useTrackedStages,
} from '../applications/useTrackedStages.ts'
import { Landing } from '../pages/Landing.tsx'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { EmptyState } from '../ui/EmptyState.tsx'
import { FormField } from '../ui/FormField.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { OriginalLinks } from './OriginalLinks.tsx'
import { loadPostings } from './postingsApi.ts'
import { PAGE_SIZE, type ListQuery, type PostingSummary } from './types.ts'
import '../styles/postings.css'

const CANTONS = [
  'AG',
  'AI',
  'AR',
  'BE',
  'BL',
  'BS',
  'FR',
  'GE',
  'GL',
  'GR',
  'JU',
  'LU',
  'NE',
  'NW',
  'OW',
  'SG',
  'SH',
  'SO',
  'SZ',
  'TG',
  'TI',
  'UR',
  'VD',
  'VS',
  'ZG',
  'ZH',
]

/** The crawled postings. A visitor without a session sees the landing page and comes back here after signing in. */
export function PostingsPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/postings" />
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
      return <PostingsList />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; postings: PostingSummary[] }

function PostingsList() {
  const auth = useAuth()
  const [params, setParams] = useSearchParams()
  const query: ListQuery = {
    q: params.get('q') ?? '',
    canton: params.get('canton') ?? '',
    source: params.get('source') ?? '',
    page: Math.max(0, Number(params.get('page')) || 0),
  }
  const key = JSON.stringify(query)
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const [text, setText] = useState(query.q)
  const [sources, setSources] = useState<string[]>([])

  const fetchPage = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadPostings(auth, JSON.parse(key) as ListQuery)
    if (result.kind === 'loaded') {
      setLoad({ state: 'ready', postings: result.postings })
      setSources((known) =>
        [
          ...new Set([
            ...known,
            ...result.postings.flatMap((p) => p.links.map((l) => l.sourceId)),
          ]),
        ].sort(),
      )
    } else if (result.kind === 'failed') {
      setLoad({ state: 'failed', message: result.message })
    }
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; loading belongs to the query and the retry button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  useEffect(() => {
    void fetchPage()
  }, [fetchPage])

  const update = (changes: Partial<ListQuery>) => {
    const next = { ...query, ...changes }
    const out = new URLSearchParams()
    if (next.q.trim()) out.set('q', next.q.trim())
    if (next.canton) out.set('canton', next.canton)
    if (next.source) out.set('source', next.source)
    if (next.page > 0) out.set('page', String(next.page))
    setParams(out)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    update({ q: text, page: 0 })
  }

  const postings = load.state === 'ready' ? load.postings : []
  const tracked = useTrackedStages(postings.map((p) => p.id))
  const sourceChoices = [...new Set([...sources, query.source].filter(Boolean))]

  return (
    <div className="page">
      <PageHeader title="Postings" />
      <p>
        <Link to="/">Back</Link>
      </p>
      <form onSubmit={submit} className="posting-filters" role="search">
        <FormField label="Search">
          {(control) => (
            <input
              {...control}
              type="search"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Title or company"
            />
          )}
        </FormField>
        <FormField label="Canton">
          {(control) => (
            <select
              {...control}
              value={query.canton}
              onChange={(e) => update({ canton: e.target.value, page: 0 })}
            >
              <option value="">All</option>
              {CANTONS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label="Source">
          {(control) => (
            <select
              {...control}
              value={query.source}
              onChange={(e) => update({ source: e.target.value, page: 0 })}
            >
              <option value="">All</option>
              {sourceChoices.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <button type="submit">Search</button>
      </form>

      {load.state === 'loading' && <p role="status">Loading postings…</p>}
      {load.state === 'failed' && (
        <Alert tone="danger">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchPage()}>
            Try again
          </button>
        </Alert>
      )}
      {load.state === 'ready' && postings.length === 0 && (
        <EmptyState title="No postings match.">
          <p>Try another search word, or clear the canton and source.</p>
        </EmptyState>
      )}
      {load.state === 'ready' && postings.length > 0 && (
        <ul className="posting-list">
          {postings.map((p) => (
            <li key={p.id}>
              <Card className="posting-card">
                <h2 className="posting-card__title">
                  <Link to={`/postings/${p.id}`}>{p.title}</Link>
                </h2>
                <p className="posting-card__meta">
                  {p.company}
                  {p.city ? ` · ${p.city}` : ''}
                  {p.canton ? ` (${p.canton})` : ''} · {p.language} · first seen{' '}
                  {p.firstSeen.slice(0, 10)}
                </p>
                <OriginalLinks links={p.links} />
                <InterestedButton
                  postingId={p.id}
                  tracked={trackedOf(tracked, p.id)}
                />
              </Card>
            </li>
          ))}
        </ul>
      )}

      <nav aria-label="Pages" className="pagination">
        <button
          type="button"
          className="secondary"
          disabled={query.page === 0}
          onClick={() => update({ page: query.page - 1 })}
        >
          Previous
        </button>
        <span>Page {query.page + 1}</span>
        <button
          type="button"
          className="secondary"
          disabled={load.state !== 'ready' || postings.length < PAGE_SIZE}
          onClick={() => update({ page: query.page + 1 })}
        >
          Next
        </button>
      </nav>
    </div>
  )
}
