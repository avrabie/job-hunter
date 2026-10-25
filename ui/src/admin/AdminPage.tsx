import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import '../styles/admin.css'
import {
  loadSchedule,
  saveSchedule,
  type Schedule,
  type ScheduleResult,
} from './adminApi.ts'

/** The crawl schedule, for administrators. A visitor without a session sees the landing page. */
export function AdminPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/admin" />
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
      return <ScheduleEditor />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'forbidden' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; schedule: Schedule }

function ScheduleEditor() {
  const auth = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const [enabled, setEnabled] = useState(false)
  const [cron, setCron] = useState('')
  const [zone, setZone] = useState('')
  const [saving, setSaving] = useState(false)
  const [rejected, setRejected] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const show = (schedule: Schedule) => {
    setLoad({ state: 'ready', schedule })
    setEnabled(schedule.enabled)
    setCron(schedule.cron)
    setZone(schedule.zone)
  }

  const fetchSchedule = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadSchedule(auth)
    if (result.kind === 'loaded') show(result.schedule)
    else if (result.kind === 'forbidden') setLoad({ state: 'forbidden' })
    else if (result.kind === 'failed')
      setLoad({ state: 'failed', message: result.message })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; loading belongs to mount and the retry button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void fetchSchedule()
  }, [fetchSchedule])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setRejected(null)
    setSaveError(null)
    const result: ScheduleResult = await saveSchedule(auth, {
      enabled,
      cron: cron.trim(),
      zone: zone.trim(),
    })
    setSaving(false)
    if (result.kind === 'loaded') show(result.schedule)
    else if (result.kind === 'invalid') setRejected(result.message)
    else if (result.kind === 'forbidden') setLoad({ state: 'forbidden' })
    else if (result.kind === 'failed') setSaveError(result.message)
  }

  return (
    <div className="page page--narrow">
      <PageHeader
        title="Crawl schedule"
        actions={
          <Link className="button button--secondary" to="/">
            Back
          </Link>
        }
      />
      {load.state === 'loading' && <p role="status">Loading the schedule…</p>}
      {load.state === 'forbidden' && (
        <Alert tone="danger">This page is for administrators.</Alert>
      )}
      {load.state === 'failed' && (
        <Alert tone="danger">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchSchedule()}>
            Try again
          </button>
        </Alert>
      )}
      {load.state === 'ready' && (
        <>
          <p className="admin-status">
            {load.schedule.enabled
              ? `Crawling is on. Next run: ${load.schedule.nextFire ?? 'unknown'}.`
              : 'Crawling is off.'}
            {load.schedule.updatedAt
              ? ` Last changed: ${load.schedule.updatedAt}.`
              : ''}
          </p>
          <Card>
            <form
              onSubmit={(e) => void submit(e)}
              className="admin-form"
              aria-label="Crawl schedule settings"
            >
              <label className="admin-form__check">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                />
                Enabled
              </label>
              <FormField label="Cron (with seconds, for example 0 0 * * * *)">
                {(control) => (
                  <input
                    {...control}
                    type="text"
                    value={cron}
                    onChange={(e) => setCron(e.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Time zone">
                {(control) => (
                  <input
                    {...control}
                    type="text"
                    value={zone}
                    onChange={(e) => setZone(e.target.value)}
                  />
                )}
              </FormField>
              {rejected && <Alert tone="danger">{rejected}</Alert>}
              {saveError && <Alert tone="danger">{saveError}</Alert>}
              <div className="admin-form__actions">
                <button type="submit" disabled={saving}>
                  Save
                </button>
              </div>
            </form>
          </Card>
        </>
      )}
    </div>
  )
}
