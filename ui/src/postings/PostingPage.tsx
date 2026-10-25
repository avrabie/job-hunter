import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { InterestedButton } from '../applications/InterestedButton.tsx'
import {
  trackedOf,
  useTrackedStages,
} from '../applications/useTrackedStages.ts'
import { Landing } from '../pages/Landing.tsx'
import { OriginalLinks } from './OriginalLinks.tsx'
import { loadPosting } from './postingsApi.ts'
import type { PostingDetail } from './types.ts'
import { UserAddedBadge } from './UserAddedBadge.tsx'

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
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </main>
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

  if (load.state === 'loading') return <p role="status">Loading posting…</p>
  if (load.state === 'notFound')
    return (
      <main>
        <h1>Posting not found</h1>
        <p>
          <Link to="/postings">Back to the postings</Link>
        </p>
      </main>
    )
  if (load.state === 'failed')
    return (
      <main>
        <div role="alert">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchPosting()}>
            Try again
          </button>
        </div>
        <p>
          <Link to="/postings">Back to the postings</Link>
        </p>
      </main>
    )

  const p = load.posting
  return (
    <main>
      <p>
        <Link to="/postings">Back to the postings</Link>
      </p>
      <h1>{p.title}</h1>
      <UserAddedBadge userAdded={p.userAdded} />
      <p>
        {p.company}
        {p.city ? ` · ${p.city}` : ''}
        {p.canton ? ` (${p.canton})` : ''} · {p.language} · first seen{' '}
        {p.firstSeen.slice(0, 10)} · last seen {p.lastSeen.slice(0, 10)}
      </p>
      <OriginalLinks links={p.links} />
      <InterestedButton postingId={p.id} tracked={trackedOf(tracked, p.id)} />
      <pre className="description">{p.description}</pre>
    </main>
  )
}
