import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Card } from '../ui/Card.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { Landing } from './Landing.tsx'

export function Home() {
  const { state, signIn } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'error':
      return (
        <div className="page page--narrow">
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
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

/** The signed-in home: where to go next. The navigation bar carries the identity and every link. */
function Overview() {
  return (
    <div className="page">
      <PageHeader
        title="Overview"
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
          <p>The jobs you are interested in, from first look to offer.</p>
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
    </div>
  )
}
