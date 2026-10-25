import { useCallback, useEffect, useState } from 'react'
import { PageLoading } from '../ui/PageLoading.tsx'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { InterestedButton } from '../applications/InterestedButton.tsx'
import {
  trackedOf,
  useTrackedStages,
} from '../applications/useTrackedStages.ts'
import { Landing } from '../pages/Landing.tsx'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { OriginalLinks } from './OriginalLinks.tsx'
import { loadPosting } from './postingsApi.ts'
import type { PostingDetail } from './types.ts'
import '../styles/postings.css'

/** One posting with its description as plain text and its original links. */
export function PostingPage() {
  const { state } = useAuth()
  const { id = '' } = useParams()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo={`/postings/${id}`} />
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
      return <PostingView id={id} />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'notFound' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; posting: PostingDetail }

function PostingView({ id }: { id: string }) {
  const auth = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const tracked = useTrackedStages([id])

  const fetchPosting = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadPosting(auth, id)
    if (result.kind === 'loaded')
      setLoad({ state: 'ready', posting: result.posting })
    else if (result.kind === 'notFound') setLoad({ state: 'notFound' })
    else if (result.kind === 'failed')
      setLoad({ state: 'failed', message: result.message })
    // `auth` is a new object whenever the sign-in state changes; loading belongs to the id and the retry button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(() => {
    void fetchPosting()
  }, [fetchPosting])

  if (load.state === 'loading')
    return <PageLoading title="Posting" text="Loading posting…" />
  if (load.state === 'notFound')
    return (
      <div className="page page--narrow">
        <PageHeader title="Posting not found" />
        <p>
          <Link to="/postings">Back to the postings</Link>
        </p>
      </div>
    )
  if (load.state === 'failed')
    return (
      <div className="page page--narrow">
        <PageHeader title="Posting" />
        <Alert tone="danger">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchPosting()}>
            Try again
          </button>
        </Alert>
        <p>
          <Link to="/postings">Back to the postings</Link>
        </p>
      </div>
    )

  const p = load.posting
  return (
    <div className="page page--narrow">
      <p>
        <Link to="/postings">Back to the postings</Link>
      </p>
      <PageHeader title={p.title} />
      <p className="posting-meta">
        {p.company}
        {p.city ? ` · ${p.city}` : ''}
        {p.canton ? ` (${p.canton})` : ''} · {p.language} · first seen{' '}
        {p.firstSeen.slice(0, 10)} · last seen {p.lastSeen.slice(0, 10)}
      </p>
      <div className="posting-actions">
        <OriginalLinks links={p.links} />
        <InterestedButton postingId={p.id} tracked={trackedOf(tracked, p.id)} />
      </div>
      <Card title="Description">
        <div className="posting-description">{p.description}</div>
      </Card>
    </div>
  )
}
