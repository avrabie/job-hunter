import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import type { Profile } from '../profile/types.ts'
import {
  ACCEPTED_TYPES,
  deleteCv,
  downloadCv,
  extractProfile,
  loadCv,
  uploadCv,
  type CvInfo,
} from './cvApi.ts'

type Busy = 'uploading' | 'extracting' | 'deleting' | null

/**
 * The CV part of the profile page: one stored CV (upload, replace, download, delete) and the step that turns it into a
 * draft for the form. `formHasContent` asks for a confirmation before a draft replaces what is already in the form.
 * The draft goes to the form as unsaved changes; saving stays the profile page's own button.
 */
export function CvSection({
  formHasContent,
  onDraft,
}: {
  formHasContent: boolean
  onDraft: (draft: Profile) => void
}) {
  const auth = useAuth()
  const [loaded, setLoaded] = useState(false)
  const [cv, setCv] = useState<CvInfo | null>(null)
  const [busy, setBusy] = useState<Busy>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const refresh = useCallback(async () => {
    const result = await loadCv(auth)
    if (result.kind === 'loaded') setCv(result.cv)
    else if (result.kind === 'none') setCv(null)
    else if (result.kind === 'failed') setError(result.message)
    setLoaded(true)
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function onFile(file: File | undefined) {
    if (!file) return
    setBusy('uploading')
    setError(null)
    setNotice(null)
    const result = await uploadCv(auth, file)
    if (result.kind === 'stored') setCv(result.cv)
    else if (result.kind === 'failed') setError(result.message)
    setBusy(null)
    if (fileInput.current) fileInput.current.value = ''
  }

  async function onDownload() {
    if (!cv) return
    setError(null)
    const result = await downloadCv(auth, cv.fileName)
    if (result.kind === 'failed') setError(result.message)
  }

  async function onDelete() {
    if (!window.confirm('Delete your stored CV?')) return
    setBusy('deleting')
    setError(null)
    setNotice(null)
    const result = await deleteCv(auth)
    if (result.kind === 'deleted') setCv(null)
    else if (result.kind === 'failed') setError(result.message)
    setBusy(null)
  }

  async function onExtract() {
    if (
      formHasContent &&
      !window.confirm(
        'Replace what is in the form with the draft from your CV?',
      )
    )
      return
    setBusy('extracting')
    setError(null)
    setNotice(null)
    const result = await extractProfile(auth)
    if (result.kind === 'draft') {
      onDraft(result.profile)
      setNotice(
        'The form now holds a draft from your CV. Nothing is saved until you press Save. Check every field first.',
      )
    } else if (result.kind === 'failed') {
      setError(result.message)
    }
    setBusy(null)
  }

  if (!loaded) return <p role="status">Loading your CV…</p>

  return (
    <section aria-labelledby="cv-heading" className="cv-section">
      <h2 id="cv-heading">Your CV</h2>
      {cv ? (
        <p>
          <strong>{cv.fileName}</strong> ·{' '}
          {Math.max(1, Math.round(cv.sizeBytes / 1024))} KB · uploaded{' '}
          {cv.uploadedAt.slice(0, 10)}
        </p>
      ) : (
        <p>
          Upload a CV (PDF, Markdown or plain text, up to 5 MB) and we can draft
          your profile from it. It is stored until you delete it.
        </p>
      )}
      <div className="actions">
        <label className="button secondary">
          {cv ? 'Replace CV' : 'Upload a CV'}
          <input
            ref={fileInput}
            type="file"
            accept={ACCEPTED_TYPES}
            hidden
            disabled={busy !== null}
            onChange={(e) => void onFile(e.target.files?.[0])}
          />
        </label>
        {cv && (
          <>
            <button
              type="button"
              className="secondary"
              onClick={() => void onDownload()}
            >
              Download
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy !== null}
              onClick={() => void onDelete()}
            >
              Delete
            </button>
            <button
              type="button"
              disabled={busy !== null}
              onClick={() => void onExtract()}
            >
              Fill the profile from my CV
            </button>
          </>
        )}
      </div>
      {busy === 'uploading' && <p role="status">Uploading…</p>}
      {busy === 'extracting' && (
        <p role="status">Reading your CV. This can take up to a minute…</p>
      )}
      {busy === 'deleting' && <p role="status">Deleting…</p>}
      {error && <p role="alert">{error}</p>}
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
    </section>
  )
}
