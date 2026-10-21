import { useAuth } from '../auth/authContext.ts'
import { Landing } from './Landing.tsx'
import { Shell } from './Shell.tsx'

export function Home() {
  const { state, signIn } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'error':
      return (
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
          <button type="button" onClick={() => void signIn()}>
            Try again
          </button>
        </main>
      )
    case 'signedOut':
      return <Landing />
    case 'signedIn': {
      const { email, name } = state.user.profile
      return <Shell identity={email ?? name ?? 'your account'} />
    }
  }
}
