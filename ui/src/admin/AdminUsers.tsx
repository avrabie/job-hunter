import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import {
  loadUser,
  loadUsers,
  type AdminApplication,
  type AdminStage,
  type AdminUser,
  type AdminUserDetail,
} from './adminUsersApi.ts'

const STAGE_LABELS: Record<AdminStage, string> = {
  INTERESTED: 'Saved',
  APPLYING: 'Applying',
  APPLIED: 'Applied',
  REJECTED: 'Rejected',
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

/** The name shown for a user: the profile's full name, else the account's display name, else the email. */
function nameOf(user: AdminUser): string {
  return user.fullName || user.displayName || user.email || user.sub
}

type ListState =
  | { state: 'loading' }
  | { state: 'forbidden' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; users: AdminUser[] }

type DetailState =
  | { state: 'idle' }
  | { state: 'loading'; sub: string }
  | { state: 'notFound' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; detail: AdminUserDetail }

/** Every user with the number of jobs they saved or are applying to; a row opens the user's details. */
export function AdminUsers() {
  const auth = useAuth()
  const [list, setList] = useState<ListState>({ state: 'loading' })
  const [detail, setDetail] = useState<DetailState>({ state: 'idle' })

  const fetchList = useCallback(async () => {
    setList({ state: 'loading' })
    const result = await loadUsers(auth)
    if (result.kind === 'loaded') setList({ state: 'ready', users: result.users })
    else if (result.kind === 'forbidden') setList({ state: 'forbidden' })
    else if (result.kind === 'failed')
      setList({ state: 'failed', message: result.message })
    // `unauthorized`: the page above starts a new sign-in.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void fetchList()
  }, [fetchList])

  const open = async (sub: string) => {
    setDetail({ state: 'loading', sub })
    const result = await loadUser(auth, sub)
    if (result.kind === 'loaded') setDetail({ state: 'ready', detail: result.detail })
    else if (result.kind === 'notFound') setDetail({ state: 'notFound' })
    else if (result.kind === 'failed')
      setDetail({ state: 'failed', message: result.message })
    else if (result.kind === 'forbidden')
      setDetail({ state: 'failed', message: 'This page is for administrators.' })
  }

  return (
    <div className="page page--narrow admin-users">
      <h2 className="admin-users__title">Users</h2>
      <p className="admin-users__muted">
        Accounts, profile contact details, and the jobs each user saved or is applying to.
      </p>

      {list.state === 'loading' && <p role="status">Loading users…</p>}
      {list.state === 'forbidden' && (
        <Alert tone="danger">This page is for administrators.</Alert>
      )}
      {list.state === 'failed' && (
        <Alert tone="danger">
          <p>{list.message}</p>
          <button type="button" className="button button--secondary" onClick={() => void fetchList()}>
            Try again
          </button>
        </Alert>
      )}
      {list.state === 'ready' && list.users.length === 0 && (
        <p>No users yet.</p>
      )}
      {list.state === 'ready' && list.users.length > 0 && (
        <Card title={`${list.users.length} users`} headingLevel={3}>
          <div className="admin-users__scroll">
            <table className="admin-users__table">
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Signed up</th>
                  <th scope="col">Saved</th>
                  <th scope="col">Applying</th>
                  <th scope="col">Applied</th>
                  <th scope="col">Rejected</th>
                  <th scope="col">Last activity</th>
                </tr>
              </thead>
              <tbody>
                {list.users.map((user) => (
                  <tr key={user.sub}>
                    <td>
                      <button
                        type="button"
                        className="admin-users__link"
                        onClick={() => void open(user.sub)}
                      >
                        {nameOf(user)}
                      </button>
                      {user.email && <div className="admin-users__muted">{user.email}</div>}
                    </td>
                    <td>{formatDate(user.createdAt)}</td>
                    <td>{user.saved}</td>
                    <td>{user.applying}</td>
                    <td>{user.applied}</td>
                    <td>{user.rejected}</td>
                    <td>{formatDate(user.lastActivity)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <UserDetail state={detail} />
    </div>
  )
}

function UserDetail({ state }: { state: DetailState }) {
  if (state.state === 'idle') return null
  if (state.state === 'loading')
    return <p role="status">Loading the user…</p>
  if (state.state === 'notFound')
    return <Alert tone="warning">This user no longer exists.</Alert>
  if (state.state === 'failed')
    return <Alert tone="danger">{state.message}</Alert>

  const { user, profile, applications } = state.detail
  const saved = applications.filter((a) => a.stage === 'INTERESTED')
  const applying = applications.filter((a) => a.stage === 'APPLYING')
  const rest = applications.filter((a) => a.stage === 'APPLIED' || a.stage === 'REJECTED')

  return (
    <Card title={nameOf(user)} headingLevel={3} className="admin-users__detail">
      <dl className="admin-users__facts">
        <dt>Email</dt>
        <dd>{user.email ?? '—'}</dd>
        <dt>Signed up</dt>
        <dd>{formatDate(user.createdAt)}</dd>
        <dt>Profile</dt>
        <dd>{user.hasProfile ? 'Yes' : 'No profile yet'}</dd>
        {profile?.headline && (
          <>
            <dt>Headline</dt>
            <dd>{profile.headline}</dd>
          </>
        )}
        {profile?.phone && (
          <>
            <dt>Phone</dt>
            <dd>{profile.phone}</dd>
          </>
        )}
        {(profile?.city || profile?.canton) && (
          <>
            <dt>Location</dt>
            <dd>{[profile.city, profile.canton].filter(Boolean).join(', ')}</dd>
          </>
        )}
        {profile?.website && (
          <>
            <dt>Website</dt>
            <dd>{profile.website}</dd>
          </>
        )}
        {profile?.linkedin && (
          <>
            <dt>LinkedIn</dt>
            <dd>{profile.linkedin}</dd>
          </>
        )}
      </dl>

      <JobList title="Saved" jobs={saved} empty="No saved jobs." />
      <JobList title="Applying" jobs={applying} empty="Not applying to any job." />
      {rest.length > 0 && <JobList title="Applied and rejected" jobs={rest} />}
    </Card>
  )
}

function JobList({
  title,
  jobs,
  empty,
}: {
  title: string
  jobs: AdminApplication[]
  empty?: string
}) {
  return (
    <section className="admin-users__jobs">
      <h4>
        {title} ({jobs.length})
      </h4>
      {jobs.length === 0 && empty && <p className="admin-users__muted">{empty}</p>}
      {jobs.length > 0 && (
        <ul>
          {jobs.map((job) => (
            <li key={job.id}>
              <a href={job.url} target="_blank" rel="noopener noreferrer">
                {job.title}
              </a>{' '}
              <span className="admin-users__muted">
                {job.company} · {STAGE_LABELS[job.stage]} · changed {formatDate(job.stageChangedAt)}
                {job.appliedOn && ` · applied ${formatDate(job.appliedOn)}`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
