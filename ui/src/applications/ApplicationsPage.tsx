import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import { ImportJob } from './ImportJob.tsx'
import {
  listApplications,
  STAGE_LABEL,
  STAGES,
  updateApplication,
  type ApplicationStage,
  type ApplicationSummary,
} from './applicationsApi.ts'

const LIVE_WARNING: Record<'CHANGED' | 'GONE', string> = {
  CHANGED: 'Warning: the posting changed after you marked it.',
  GONE: 'Warning: the posting is no longer listed.',
}

/** The user's applications, grouped by stage. Signed-out visitors see the landing page. */
export function ApplicationsPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/applications" />
    case 'error':
      return (
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </main>
      )
    case 'signedIn':
      return <ApplicationsList />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; applications: ApplicationSummary[] }

function ApplicationsList() {
  const auth = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const [notice, setNotice] = useState<string | null>(null)

  const fetchList = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await listApplications(auth)
    if (result.kind === 'loaded')
      setLoad({ state: 'ready', applications: result.applications })
    else if (result.kind === 'failed')
      setLoad({ state: 'failed', message: result.message })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; loading belongs to the retry button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void fetchList()
  }, [fetchList])

  async function changeStage(id: string, stage: ApplicationStage) {
    setNotice(null)
    const result = await updateApplication(auth, id, { stage })
    if (result.kind === 'done')
      setLoad((current) =>
        current.state === 'ready'
          ? {
              state: 'ready',
              applications: current.applications.map((a) =>
                a.id === id ? { ...a, stage } : a,
              ),
            }
          : current,
      )
    else if (result.kind === 'failed') setNotice(result.message)
  }

  return (
    <main>
      <h1>Job tracking</h1>
      <p>
        <Link to="/postings">Postings</Link>
      </p>

      <ImportJob />

      {load.state === 'loading' && <p role="status">Loading applications…</p>}
      {load.state === 'failed' && (
        <div role="alert">
          <p>{load.message}</p>
          <button type="button" onClick={() => void fetchList()}>
            Try again
          </button>
        </div>
      )}
      {notice && <p role="alert">{notice}</p>}

      {load.state === 'ready' && load.applications.length === 0 && (
        <p>
          You have not marked any postings yet. Open a posting and press
          Interested.
        </p>
      )}

      {load.state === 'ready' &&
        load.applications.length > 0 &&
        STAGES.map((stage) => {
          const rows = load.applications.filter((a) => a.stage === stage)
          return (
            <section
              key={stage}
              aria-labelledby={`stage-${stage}`}
              className="stage"
            >
              <h2 id={`stage-${stage}`}>{STAGE_LABEL[stage]}</h2>
              {rows.length === 0 ? (
                <p>None.</p>
              ) : (
                <ul className="applications">
                  {rows.map((a) => (
                    <li key={a.id}>
                      <h3>
                        <Link to={`/applications/${a.id}`}>{a.title}</Link>
                      </h3>
                      <p>
                        {a.company} · stage since{' '}
                        {a.stageChangedAt.slice(0, 10)}
                        {a.appliedOn ? ` · applied ${a.appliedOn}` : ''}
                      </p>
                      {a.liveStatus !== 'OPEN' && (
                        <p className="warning">{LIVE_WARNING[a.liveStatus]}</p>
                      )}
                      <select
                        aria-label={`Stage of ${a.title}`}
                        value={a.stage}
                        onChange={(e) =>
                          void changeStage(
                            a.id,
                            e.target.value as ApplicationStage,
                          )
                        }
                      >
                        {STAGES.map((s) => (
                          <option key={s} value={s}>
                            {STAGE_LABEL[s]}
                          </option>
                        ))}
                      </select>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )
        })}
    </main>
  )
}
