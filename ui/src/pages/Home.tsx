import { useEffect, useState } from 'react'
import { PageLoading } from '../ui/PageLoading.tsx'
import { Link } from 'react-router'
import {
  listApplications,
  STAGE_LABEL,
  STAGES,
  type ApplicationStage,
} from '../applications/applicationsApi.ts'
import { useAuth } from '../auth/authContext.ts'
import { Card } from '../ui/Card.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { Landing } from './Landing.tsx'

export function Home() {
  const { state, signIn } = useAuth()
  switch (state.status) {
    case 'loading':
      return <PageLoading title="Home" />
    case 'error':
      return (
        <div className="page page--narrow">
          <PageHeader
            title="Sign-in failed"
            lead="We could not reach the sign-in service."
          />
          <button type="button" onClick={() => void signIn()}>
            Try again
          </button>
        </div>
      )
    case 'signedOut':
      return <Landing />
    case 'signedIn':
      return <Overview />
  }
}

/** The signed-in home: a greeting, where to go next and how many jobs sit in each stage. */
function Overview() {
  const { state } = useAuth()
  // Only the name or email claim of the ID token profile is read; no token value ever reaches the page.
  const profile = state.status === 'signedIn' ? state.user.profile : null
  const who = profile?.name ?? profile?.email
  return (
    <div className="page">
      <PageHeader
        title={who ? `Welcome, ${who}` : 'Welcome'}
        lead="Find Swiss software-engineering jobs that fit you and keep track of your applications."
      />
      <div className="card-grid">
        <Card
          title="Matches"
          actions={
            <Link className="button" to="/matches">
              See your matches
            </Link>
          }
        >
          <p>Postings ranked against your profile, with the reasons.</p>
        </Card>
        <Card
          title="Job tracking"
          actions={
            <Link className="button" to="/applications">
              Open job tracking
            </Link>
          }
        >
          <StageCounts />
        </Card>
        <Card
          title="Profile"
          actions={
            <Link className="button" to="/profile">
              Edit your profile
            </Link>
          }
        >
          <p>Your skills and experience decide the matches and the CV.</p>
        </Card>
      </div>
      <nav aria-label="Quick links" className="quick-links">
        <h2 className="quick-links__title">Quick links</h2>
        <ul className="quick-links__list">
          <li>
            <Link to="/postings">Postings</Link>
          </li>
          <li>
            <Link to="/export">Export CV</Link>
          </li>
          <li>
            <Link to="/publish">Public CV</Link>
          </li>
        </ul>
      </nav>
    </div>
  )
}

type Counts =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | {
      state: 'ready'
      total: number
      perStage: Record<ApplicationStage, number>
    }

/**
 * Tracked jobs per stage, counted from the existing list endpoint (no endpoint of its own). A failure stays inside
 * the card, so the rest of the overview keeps working.
 */
function StageCounts() {
  const auth = useAuth()
  const [counts, setCounts] = useState<Counts>({ state: 'loading' })

  useEffect(() => {
    let current = true
    void listApplications(auth).then((result) => {
      if (!current) return
      if (result.kind === 'loaded') {
        const perStage = Object.fromEntries(
          STAGES.map((s) => [s, 0]),
        ) as Record<ApplicationStage, number>
        // A stage this UI does not know is left out of the counts rather than shown as an unlabelled row.
        for (const a of result.applications)
          if (a.stage in perStage) perStage[a.stage] += 1
        setCounts({
          state: 'ready',
          total: result.applications.length,
          perStage,
        })
      } else if (result.kind === 'failed')
        setCounts({ state: 'failed', message: result.message })
      // `unauthorized`: the tokens are gone and the app shows the landing page.
    })
    return () => {
      current = false
    }
    // `auth` is a new object whenever the sign-in state changes; the counts load once per visit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  switch (counts.state) {
    case 'loading':
      return <p className="stage-counts__note">Counting your tracked jobs…</p>
    case 'failed':
      return (
        <p className="stage-counts__note">
          Your tracked jobs could not be counted. {counts.message}
        </p>
      )
    case 'ready':
      return counts.total === 0 ? (
        <p>
          You are not tracking any jobs yet. Mark a posting as Interested to
          start.
        </p>
      ) : (
        <ul className="stage-counts" aria-label="Tracked jobs per stage">
          {STAGES.map((s) => (
            <li key={s} className="stage-counts__item">
              <span className="stage-counts__count">{counts.perStage[s]}</span>{' '}
              <span className="stage-counts__label">{STAGE_LABEL[s]}</span>
            </li>
          ))}
        </ul>
      )
  }
}
