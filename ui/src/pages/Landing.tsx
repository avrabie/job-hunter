import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'

/** What a signed-out visitor sees. It shows no user data and makes no call to the API. */
export function Landing() {
  const { signIn } = useAuth()
  return (
    <main>
      <h1>Job Hunter</h1>
      <p>
        Finds Swiss software-engineering jobs that fit you and drafts your
        application. You review everything.
      </p>
      <div className="actions">
        <button type="button" onClick={() => void signIn()}>
          Sign in
        </button>
        <Link className="button secondary" to="/register">
          Register
        </Link>
      </div>
    </main>
  )
}
