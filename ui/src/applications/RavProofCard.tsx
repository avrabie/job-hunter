import { useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import '../styles/applications.css'
import { loadRavProof, type RavProof } from './applicationsApi.ts'

const SIGN_IN_AGAIN = 'Please sign in again.'

/** The fields in the order of the RAV form. */
const FIELDS: { key: keyof RavProof; label: string }[] = [
  { key: 'companyAddress', label: 'Company address' },
  { key: 'position', label: 'Position' },
  { key: 'contactPerson', label: 'Contact person' },
  { key: 'contactPhone', label: 'Contact phone' },
  { key: 'contactEmail', label: 'Contact email' },
  { key: 'postalCode', label: 'Postal code (Postleitzahl)' },
  { key: 'postingUrl', label: 'Original posting URL' },
]

type State =
  | { state: 'hidden' }
  | { state: 'loading' }
  | { state: 'loaded'; proof: RavProof }
  | { state: 'failed'; message: string }

/**
 * The fields for the Swiss RAV job-search proof of an applied job. Each field has a copy action; a field the
 * application does not have is shown as `null` and cannot be copied.
 */
export function RavProofCard({ applicationId }: { applicationId: string }) {
  const auth = useAuth()
  const [view, setView] = useState<State>({ state: 'hidden' })
  const [copied, setCopied] = useState<string | null>(null)
  const [copyFailed, setCopyFailed] = useState(false)

  async function toggle() {
    if (view.state !== 'hidden') {
      setView({ state: 'hidden' })
      return
    }
    setView({ state: 'loading' })
    const result = await loadRavProof(auth, applicationId)
    if (result.kind === 'loaded')
      setView({ state: 'loaded', proof: result.proof })
    else if (result.kind === 'notApplied')
      setView({
        state: 'failed',
        message: 'Mark the application as applied to see these fields.',
      })
    else if (result.kind === 'notFound')
      setView({
        state: 'failed',
        message: 'That application no longer exists.',
      })
    else if (result.kind === 'failed')
      setView({ state: 'failed', message: result.message })
    else setView({ state: 'failed', message: SIGN_IN_AGAIN })
  }

  async function copy(label: string, value: string) {
    setCopyFailed(false)
    try {
      await navigator.clipboard.writeText(value)
      setCopied(label)
    } catch {
      setCopied(null)
      setCopyFailed(true)
    }
  }

  return (
    <Card title="RAV proof fields" headingLevel={2} className="rav-proof">
      <p>
        The fields of the Swiss RAV job-search proof for this application. Copy
        each value into the form.
      </p>
      <div className="application-actions">
        <button
          type="button"
          className="button--secondary"
          disabled={view.state === 'loading'}
          onClick={() => void toggle()}
        >
          {view.state === 'hidden' ? 'Show RAV fields' : 'Hide RAV fields'}
        </button>
      </div>

      {view.state === 'loading' && <p>Loading…</p>}
      {view.state === 'failed' && <Alert tone="danger">{view.message}</Alert>}
      {view.state === 'loaded' && (
        <>
          <dl className="rav-fields">
            {FIELDS.map(({ key, label }) => {
              const value = view.proof[key]
              return (
                <div className="rav-fields__row" key={key}>
                  <dt>{label}</dt>
                  <dd>
                    <span className="rav-fields__value">{String(value)}</span>
                    <button
                      type="button"
                      className="button--secondary"
                      disabled={value === null}
                      aria-label={`Copy ${label}`}
                      onClick={() => void copy(label, value ?? '')}
                    >
                      Copy
                    </button>
                  </dd>
                </div>
              )
            })}
          </dl>
          {copied !== null && <p aria-live="polite">Copied {copied}.</p>}
          {copyFailed && (
            <p aria-live="polite">
              The value could not be copied. Select it and copy by hand.
            </p>
          )}
        </>
      )}
    </Card>
  )
}
