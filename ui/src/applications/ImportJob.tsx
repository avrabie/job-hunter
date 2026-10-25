import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import '../styles/applications.css'
import {
  confirmJobImport,
  previewJobImport,
  type ImportErrorCode,
  type JobDraft,
  type JobEdits,
} from './jobImportApi.ts'

/** Failures after which the page can be read from a pasted description instead. */
const PASTE_CODES: readonly ImportErrorCode[] = [
  'unreadable',
  'not_html',
  'too_large',
  'blocked_address',
  'unreachable',
]

/**
 * "Add job from URL": the user gives a posting URL, reviews what the server read, and saves it as a tracked job.
 * When the server cannot read the page, the user can paste the description and try again with it.
 */
export function ImportJob() {
  const auth = useAuth()
  const [url, setUrl] = useState('')
  const [pasteOpen, setPasteOpen] = useState(false)
  const [paste, setPaste] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [draft, setDraft] = useState<JobDraft | null>(null)

  if (draft)
    return (
      <ReviewForm
        key={draft.draftId}
        draft={draft}
        onClose={(notice) => {
          setDraft(null)
          setMessage(notice)
        }}
      />
    )

  async function preview(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    const text = pasteOpen && paste.trim() ? paste.trim() : undefined
    const result = await previewJobImport(auth, { url: url.trim(), text })
    setBusy(false)
    if (result.kind === 'previewed') setDraft(result.draft)
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    else if (result.kind === 'failed') {
      setMessage(result.message)
      if (result.code && PASTE_CODES.includes(result.code)) setPasteOpen(true)
    }
  }

  return (
    // A plain box, not a titled section: the heading is the first thing in it, and an empty list has no regions.
    <Card className="import-job">
      <h2 className="card__title">Add job from URL</h2>
      <form onSubmit={(e) => void preview(e)}>
        <FormField label="Job posting URL">
          {(control) => (
            <input
              {...control}
              type="text"
              inputMode="url"
              autoComplete="off"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
            />
          )}
        </FormField>
        {pasteOpen && (
          <FormField label="Job description">
            {(control) => (
              <textarea
                {...control}
                rows={8}
                value={paste}
                onChange={(e) => setPaste(e.target.value)}
              />
            )}
          </FormField>
        )}
        <div className="application-actions">
          <button type="submit" disabled={busy || !url.trim()}>
            Preview
          </button>
        </div>
      </form>
      {message && <Alert tone="danger">{message}</Alert>}
    </Card>
  )
}

/** The read job, editable before it is saved. `onClose` returns to the URL step; a notice explains why when needed. */
function ReviewForm({
  draft,
  onClose,
}: {
  draft: JobDraft
  onClose: (notice: string | null) => void
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  const [edits, setEdits] = useState<JobEdits>({
    title: draft.title,
    company: draft.company,
    location: draft.location,
    description: draft.description,
  })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const change = (field: keyof JobEdits, value: string) =>
    setEdits((current) => ({ ...current, [field]: value }))

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    const result = await confirmJobImport(auth, draft.draftId, edits)
    setBusy(false)
    if (result.kind === 'imported')
      navigate(`/applications/${result.job.applicationId}`, {
        state: { alreadyTracked: !result.job.created },
      })
    else if (result.kind === 'failed') {
      if (result.code === 'draft_not_found') onClose(result.message)
      else setMessage(result.message)
    }
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
  }

  const complete =
    edits.title.trim() !== '' &&
    edits.company.trim() !== '' &&
    edits.description.trim() !== ''

  return (
    <Card className="import-job">
      <h2 className="card__title">Review the job</h2>
      <Alert tone="info">
        This job is saved only in your own job tracking; other users cannot see
        it.
      </Alert>
      <form onSubmit={(e) => void save(e)}>
        <FormField label="Title">
          {(control) => (
            <input
              {...control}
              value={edits.title}
              onChange={(e) => change('title', e.target.value)}
            />
          )}
        </FormField>
        <FormField label="Company">
          {(control) => (
            <input
              {...control}
              value={edits.company}
              onChange={(e) => change('company', e.target.value)}
            />
          )}
        </FormField>
        <FormField label="Location">
          {(control) => (
            <input
              {...control}
              value={edits.location}
              onChange={(e) => change('location', e.target.value)}
            />
          )}
        </FormField>
        <FormField label="Description">
          {(control) => (
            <textarea
              {...control}
              rows={12}
              value={edits.description}
              onChange={(e) => change('description', e.target.value)}
            />
          )}
        </FormField>
        <div className="application-actions">
          <button type="submit" disabled={busy || !complete}>
            Save and track
          </button>
          <button
            type="button"
            className="button--secondary"
            onClick={() => onClose(null)}
          >
            Start over
          </button>
        </div>
      </form>
      {message && <Alert tone="danger">{message}</Alert>}
    </Card>
  )
}
