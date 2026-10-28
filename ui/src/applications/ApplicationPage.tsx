import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { PageLoading } from '../ui/PageLoading.tsx'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { safeHttpUrl } from '../postings/links.ts'
import { Landing } from '../pages/Landing.tsx'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import '../styles/applications.css'
import {
  loadApplication,
  removeApplication,
  STAGE_LABEL,
  STAGES,
  updateApplication,
  type ApplicationDetail,
  type ApplicationStage,
} from './applicationsApi.ts'
import {
  CompanyContactFields,
  contactInput,
  type ContactKey,
} from './CompanyContactFields.tsx'
import { LetterWorkspace } from './LetterWorkspace.tsx'
import { RavProofCard } from './RavProofCard.tsx'

const LIVE_NOTICE: Record<'CHANGED' | 'GONE', string> = {
  CHANGED:
    'The posting changed after you marked it. The text below is the copy you saw, not the current posting.',
  GONE: 'The posting is no longer listed. The text below is the last copy we have.',
}

/** One application: the frozen description, the stage, dates, notes, removal and the letters. */
export function ApplicationPage() {
  const { state } = useAuth()
  const { id = '' } = useParams()
  // Set by the import form when the posting was already tracked, so the page can say so.
  const location = useLocation()
  const alreadyTracked =
    (location.state as { alreadyTracked?: unknown } | null)?.alreadyTracked ===
    true
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo={`/applications/${id}`} />
    case 'error':
      return (
        <div className="page page--narrow applications-page">
          <PageHeader title="Sign-in failed" />
          <p>We could not reach the sign-in service.</p>
        </div>
      )
    case 'signedIn':
      return <ApplicationView id={id} alreadyTracked={alreadyTracked} />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'notFound' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; application: ApplicationDetail }

function ApplicationView({
  id,
  alreadyTracked,
}: {
  id: string
  alreadyTracked: boolean
}) {
  const auth = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })

  // Reloads without the loading state, so the letter workspace below keeps what the user typed.
  const refresh = useCallback(async () => {
    const result = await loadApplication(auth, id)
    if (result.kind === 'loaded')
      setLoad({ state: 'ready', application: result.application })
    else if (result.kind === 'notFound') setLoad({ state: 'notFound' })
    else if (result.kind === 'failed')
      setLoad({ state: 'failed', message: result.message })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; the load belongs to the id.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(() => {
    setLoad({ state: 'loading' })
    void refresh()
  }, [refresh])

  if (load.state === 'loading')
    return <PageLoading title="Application" text="Loading application…" />
  if (load.state === 'notFound')
    return (
      <div className="page page--narrow applications-page">
        <PageHeader title="Application not found" />
        <p>
          <Link to="/applications">Back to job tracking</Link>
        </p>
      </div>
    )
  if (load.state === 'failed')
    return (
      <div className="page page--narrow applications-page">
        <p>
          <Link to="/applications">Back to job tracking</Link>
        </p>
        <Alert tone="danger">
          <p>{load.message}</p>
          <button type="button" onClick={() => void refresh()}>
            Try again
          </button>
        </Alert>
      </div>
    )

  const a = load.application
  const original = safeHttpUrl(a.url)
  return (
    <div className="page page--narrow applications-page">
      <p>
        <Link to="/applications">Back to job tracking</Link>
      </p>
      <PageHeader title={a.title} lead={a.company} />
      {alreadyTracked && (
        <Alert tone="info">This job was already in your job tracking.</Alert>
      )}
      {a.liveStatus !== 'OPEN' && (
        <Alert tone="warning">{LIVE_NOTICE[a.liveStatus]}</Alert>
      )}
      {original && (
        <p>
          <a href={original} target="_blank" rel="noopener noreferrer">
            Open original posting
          </a>
        </p>
      )}
      <p className="application-meta">
        Captured {a.capturedAt.slice(0, 10)}
        {a.postingLastSeen
          ? ` · posting last seen ${a.postingLastSeen.slice(0, 10)}`
          : ''}
      </p>

      <Card title="Job description (as captured)" headingLevel={2}>
        <pre className="application-description">{a.description}</pre>
      </Card>

      <Editor key={a.id} application={a} onChanged={() => void refresh()} />

      {a.stage === 'APPLIED' && (
        // Keyed by the details so the fields shown are never older than the saved ones.
        <RavProofCard key={JSON.stringify(a.contact)} applicationId={a.id} />
      )}

      <LetterWorkspace applicationId={a.id} />
    </div>
  )
}

/** The editable parts: stage, applied date, notes, company and contact, and removal. `onChanged` reloads the application after a save. */
function Editor({
  application,
  onChanged,
}: {
  application: ApplicationDetail
  onChanged: () => void
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  const [notes, setNotes] = useState(application.notes)
  const [appliedOn, setAppliedOn] = useState(application.appliedOn ?? '')
  const [contact, setContact] = useState(contactInput(application.contact))
  const [saved, setSaved] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setMessage(null)
    setSaved(false)
    const result = await updateApplication(auth, application.id, {
      notes,
      appliedOn: appliedOn || null,
      contact,
    })
    if (result.kind === 'done') {
      setSaved(true)
      onChanged()
    } else if (result.kind === 'failed') setMessage(result.message)
  }

  async function changeStage(stage: ApplicationStage) {
    setMessage(null)
    const result = await updateApplication(auth, application.id, { stage })
    if (result.kind === 'done') onChanged()
    else if (result.kind === 'failed') setMessage(result.message)
  }

  async function remove() {
    setMessage(null)
    const result = await removeApplication(auth, application.id)
    if (result.kind === 'done') navigate('/applications')
    else if (result.kind === 'failed') {
      setMessage(result.message)
      setConfirming(false)
    }
  }

  return (
    <Card>
      <FormField label="Stage">
        {(control) => (
          <select
            {...control}
            value={application.stage}
            onChange={(e) =>
              void changeStage(e.target.value as ApplicationStage)
            }
          >
            {STAGES.map((s) => (
              <option key={s} value={s}>
                {STAGE_LABEL[s]}
              </option>
            ))}
          </select>
        )}
      </FormField>

      <form onSubmit={save}>
        <FormField label="Applied on">
          {(control) => (
            <input
              {...control}
              type="date"
              value={appliedOn}
              onChange={(e) => setAppliedOn(e.target.value)}
            />
          )}
        </FormField>
        <FormField label="Notes">
          {(control) => (
            <textarea
              {...control}
              value={notes}
              maxLength={5000}
              rows={5}
              onChange={(e) => {
                setNotes(e.target.value)
                setSaved(false)
              }}
            />
          )}
        </FormField>
        <fieldset id="company-contact">
          <legend>Company &amp; contact</legend>
          <CompanyContactFields
            value={contact}
            onChange={(key: ContactKey, value) => {
              setContact((current) => ({ ...current, [key]: value }))
              setSaved(false)
            }}
          />
        </fieldset>
        <div className="application-actions">
          <button type="submit">Save</button>
          {saved && <span role="status">Saved.</span>}
        </div>
      </form>

      {message && <Alert tone="danger">{message}</Alert>}

      {confirming ? (
        <Alert tone="warning">
          <p>Remove this application and its letters? This cannot be undone.</p>
          <div className="application-actions">
            <button
              type="button"
              className="button--danger"
              onClick={() => void remove()}
            >
              Yes, remove
            </button>
            <button
              type="button"
              className="button--secondary"
              onClick={() => setConfirming(false)}
            >
              Cancel
            </button>
          </div>
        </Alert>
      ) : (
        <div className="application-actions">
          <button
            type="button"
            className="button--danger"
            onClick={() => setConfirming(true)}
          >
            Remove application
          </button>
        </div>
      )}
    </Card>
  )
}
