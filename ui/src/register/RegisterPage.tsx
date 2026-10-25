import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { register, type RegisterResult } from './register.ts'

type Problem = Exclude<RegisterResult, { kind: 'created' }> | null

export function RegisterPage() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [problem, setProblem] = useState<Problem>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setProblem(null)
    const result = await register({
      email,
      password,
      ...(firstName.trim() ? { firstName: firstName.trim() } : {}),
      ...(lastName.trim() ? { lastName: lastName.trim() } : {}),
    })
    if (result.kind === 'created') {
      // The account exists; the user now signs in at the auth service.
      setPassword('')
      await signIn()
      return
    }
    setBusy(false)
    setProblem(result)
  }

  const fields = problem?.kind === 'invalid' ? problem.fields : {}

  return (
    <div className="page page--narrow">
      <h1>Create an account</h1>
      <form onSubmit={(e) => void onSubmit(e)} noValidate>
        <Field label="Email" error={fields.email}>
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label="Password" error={fields.password}>
          <input
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Field label="First name (optional)" error={fields.firstName}>
          <input
            autoComplete="given-name"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
          />
        </Field>
        <Field label="Last name (optional)" error={fields.lastName}>
          <input
            autoComplete="family-name"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
          />
        </Field>
        {problem?.kind === 'taken' && (
          <p role="alert">
            This email is already registered. Try signing in instead.
          </p>
        )}
        {problem?.kind === 'unavailable' && (
          <p role="alert">
            Registration is unavailable right now. Please try again.
          </p>
        )}
        <button type="submit" disabled={busy}>
          Register
        </button>
      </form>
      <p>
        <Link to="/">Back</Link>
      </p>
    </div>
  )
}

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label>
      {label}
      {children}
      {error && <span role="alert">{error}</span>}
    </label>
  )
}
