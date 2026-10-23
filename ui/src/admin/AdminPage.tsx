import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
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
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </main>
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
    <main>
      <h1>Crawl schedule</h1>
      <p>
        <Link to="/">Back</Link>
      </p>
      {load.state === 'loading' && <p role="status">Loading the schedule…</p>}
      {load.state === 'forbidden' && (
        <p role="alert">This page is for administrators.</p>
      )}
      {load.state === 'failed' && (
        <div role="alert">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchSchedule()}>
            Try again
          </button>
        </div>
      )}
      {load.state === 'ready' && (
        <>
          <p>
            {load.schedule.enabled
              ? `Crawling is on. Next run: ${load.schedule.nextFire ?? 'unknown'}.`
              : 'Crawling is off.'}
            {load.schedule.updatedAt
              ? ` Last changed: ${load.schedule.updatedAt}.`
              : ''}
          </p>
          <form onSubmit={(e) => void submit(e)} className="filters">
            <label>
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
              />{' '}
              Enabled
            </label>
            <label>
              Cron (with seconds, for example 0 0 * * * *)
              <input
                type="text"
                value={cron}
                onChange={(e) => setCron(e.target.value)}
              />
            </label>
            <label>
              Time zone
              <input
                type="text"
                value={zone}
                onChange={(e) => setZone(e.target.value)}
              />
            </label>
            <button type="submit" disabled={saving}>
              Save
            </button>
          </form>
          {rejected && <p role="alert">{rejected}</p>}
          {saveError && <p role="alert">{saveError}</p>}
        </>
      )}
    </main>
  )
}
