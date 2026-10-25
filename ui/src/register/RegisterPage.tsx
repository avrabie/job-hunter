import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import '../styles/account.css'
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
      <PageHeader title="Create an account" />
      <Card>
        <form
          onSubmit={(e) => void onSubmit(e)}
          noValidate
          className="account-form"
        >
          <FormField label="Email" error={fields.email}>
            {(control) => (
              <input
                {...control}
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            )}
          </FormField>
          <FormField label="Password" error={fields.password}>
            {(control) => (
              <input
                {...control}
                type="password"
                autoComplete="new-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}
          </FormField>
          <FormField label="First name (optional)" error={fields.firstName}>
            {(control) => (
              <input
                {...control}
                autoComplete="given-name"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
            )}
          </FormField>
          <FormField label="Last name (optional)" error={fields.lastName}>
            {(control) => (
              <input
                {...control}
                autoComplete="family-name"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            )}
          </FormField>
          {problem?.kind === 'taken' && (
            <Alert tone="danger">
              This email is already registered. Try signing in instead.
            </Alert>
          )}
          {problem?.kind === 'unavailable' && (
            <Alert tone="danger">
              Registration is unavailable right now. Please try again.
            </Alert>
          )}
          <div className="account-actions">
            <button type="submit" disabled={busy}>
              Register
            </button>
            <Link className="button button--ghost" to="/">
              Back
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}
