import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Alert } from '../ui/Alert.tsx'
import '../styles/applications.css'
import { STAGE_LABEL, trackPosting } from './applicationsApi.ts'
import type { TrackedStage } from './useTrackedStages.ts'

/**
 * The "Interested" action of one posting. `tracked` is the stage the user already has for it: `undefined` while that is
 * still being looked up (nothing is shown yet), `null` when the posting is not tracked. Pressing the button tracks the
 * posting and shows the stage with a link to the application. A failure shows a message and keeps the button.
 */
export function InterestedButton({
  postingId,
  tracked,
}: {
  postingId: string
  tracked: TrackedStage | null | undefined
}) {
  const auth = useAuth()
  const [own, setOwn] = useState<TrackedStage | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const current = own ?? tracked
  if (current === undefined) return null

  if (current) {
    return (
      <p className="interested__status">
        Stage: {STAGE_LABEL[current.stage]} ·{' '}
        <Link to={`/applications/${current.applicationId}`}>
          Open application
        </Link>
      </p>
    )
  }

  async function markInterested() {
    setBusy(true)
    setMessage(null)
    const result = await trackPosting(auth, postingId)
    setBusy(false)
    if (result.kind === 'tracked')
      setOwn({
        applicationId: result.application.id,
        stage: result.application.stage,
      })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    else if (result.kind === 'failed') setMessage(result.message)
  }

  return (
    <div className="interested">
      <button
        type="button"
        className="button--secondary"
        disabled={busy}
        onClick={() => void markInterested()}
      >
        Interested
      </button>
      {message && <Alert tone="danger">{message}</Alert>}
    </div>
  )
}
