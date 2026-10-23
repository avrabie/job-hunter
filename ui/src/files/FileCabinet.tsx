import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import { extractProfile } from '../cv/cvApi.ts'
import type { Profile } from '../profile/types.ts'
import { formatSize } from './format.ts'
import {
  ACCEPTED_CV_TYPES,
  clearMaster,
  deleteFile,
  listFiles,
  requestDownload,
  setMaster,
  uploadFile,
  type FileInfo,
  type FileKind,
} from './filesApi.ts'

const KIND_LABEL: Record<FileKind, string> = { CV: 'CV', OTHER: 'Other file' }

type Listing =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready'; files: FileInfo[] }

type Busy = 'deleting' | 'master' | 'downloading' | null

/**
 * The file cabinet on the profile page: the user's files, uploads with progress, download, delete and the choice of the
 * master CV. The master CV is what the profile is drafted from. `formHasContent` asks for a confirmation before a draft
 * replaces what is already in the form. The draft goes to the form as unsaved changes; saving stays the profile page's
 * own button. `onMasterChange` is told when the user chooses, clears or deletes the master CV.
 */
export function FileCabinet({
  formHasContent,
  onDraft,
  onMasterChange,
}: {
  formHasContent: boolean
  onDraft: (draft: Profile) => void
  onMasterChange?: (hasMaster: boolean) => void
}) {
  const auth = useAuth()
  const [listing, setListing] = useState<Listing>({ state: 'loading' })
  const [kind, setKind] = useState<FileKind>('CV')
  const [upload, setUpload] = useState<{
    name: string
    fraction: number
  } | null>(null)
  const [busy, setBusy] = useState<Busy>(null)
  const [extracting, setExtracting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const controller = useRef<AbortController | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const master =
    listing.state === 'ready' ? listing.files.find((f) => f.master) : undefined

  const load = useCallback(async () => {
    setListing({ state: 'loading' })
    const result = await listFiles(auth)
    if (result.kind === 'loaded')
      setListing({ state: 'ready', files: result.files })
    else if (result.kind === 'failed')
      setListing({ state: 'failed', message: result.message })
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  // Leaving the page while a file is uploading cancels the upload.
  useEffect(() => () => controller.current?.abort(), [])

  function edit(change: (files: FileInfo[]) => FileInfo[]) {
    setListing((current) =>
      current.state === 'ready'
        ? { state: 'ready', files: change(current.files) }
        : current,
    )
  }

  async function onFile(file: File | undefined) {
    if (!file) return
    const cancel = new AbortController()
    controller.current = cancel
    setError(null)
    setUpload({ name: file.name, fraction: 0 })
    const result = await uploadFile(
      auth,
      file,
      kind,
      (fraction) => setUpload((u) => (u ? { ...u, fraction } : u)),
      cancel.signal,
    )
    controller.current = null
    setUpload(null)
    if (fileInput.current) fileInput.current.value = ''
    if (result.kind === 'stored') edit((files) => [result.file, ...files])
    else if (result.kind === 'failed') setError(result.message)
  }

  async function onDownload(id: string) {
    setBusy('downloading')
    setError(null)
    const result = await requestDownload(auth, id)
    if (result.kind === 'failed') setError(result.message)
    setBusy(null)
  }

  async function onDelete(id: string) {
    if (!window.confirm('Delete this file?')) return
    setBusy('deleting')
    setError(null)
    const result = await deleteFile(auth, id)
    if (result.kind === 'done') {
      edit((files) => files.filter((f) => f.id !== id))
      if (master?.id === id) onMasterChange?.(false)
    } else if (result.kind === 'failed') setError(result.message)
    setBusy(null)
  }

  async function onMaster(id: string) {
    setBusy('master')
    setError(null)
    const result = await setMaster(auth, id)
    if (result.kind === 'done') {
      edit((files) => files.map((f) => ({ ...f, master: f.id === id })))
      onMasterChange?.(true)
    } else if (result.kind === 'failed') setError(result.message)
    setBusy(null)
  }

  async function onClearMaster() {
    setBusy('master')
    setError(null)
    const result = await clearMaster(auth)
    if (result.kind === 'done') {
      edit((files) => files.map((f) => ({ ...f, master: false })))
      onMasterChange?.(false)
    } else if (result.kind === 'failed') setError(result.message)
    setBusy(null)
  }

  /** Asks the backend to read the master CV. The draft is not saved anywhere by this call. */
  async function onExtract() {
    if (
      formHasContent &&
      !window.confirm(
        'Replace what is in the form with the draft from your CV?',
      )
    )
      return
    setExtracting(true)
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
    setExtracting(false)
  }

  return (
    <section aria-labelledby="files-heading" className="file-cabinet">
      <h2 id="files-heading">Your files</h2>

      {listing.state === 'loading' && <p role="status">Loading your files…</p>}
      {listing.state === 'failed' && (
        <>
          <p role="alert">{listing.message}</p>
          <button
            type="button"
            className="secondary"
            onClick={() => void load()}
          >
            Try again
          </button>
        </>
      )}
      {listing.state === 'ready' && listing.files.length === 0 && (
        <p>
          You have no files yet. Upload a CV, or any other file you want to
          keep.
        </p>
      )}
      {listing.state === 'ready' && listing.files.length > 0 && (
        <ul className="files">
          {listing.files.map((f) => (
            <li key={f.id}>
              <strong>{f.name}</strong> {f.master && <span>Master CV</span>}
              <div>
                {KIND_LABEL[f.kind]} · {formatSize(f.sizeBytes)} · uploaded{' '}
                {f.uploadedAt.slice(0, 10)}
              </div>
              <div className="actions compact">
                <button
                  type="button"
                  className="secondary"
                  disabled={busy !== null}
                  onClick={() => void onDownload(f.id)}
                >
                  Download
                </button>
                {f.kind === 'CV' && !f.master && (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy !== null}
                    onClick={() => void onMaster(f.id)}
                  >
                    Use as master CV
                  </button>
                )}
                {f.master && (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy !== null}
                    onClick={() => void onClearMaster()}
                  >
                    Remove master
                  </button>
                )}
                <button
                  type="button"
                  className="secondary"
                  disabled={busy !== null}
                  onClick={() => void onDelete(f.id)}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {listing.state === 'ready' && (
        <div className="master">
          <div className="actions">
            <button
              type="button"
              disabled={!master || extracting}
              onClick={() => void onExtract()}
            >
              Fill the profile from my CV
            </button>
          </div>
          {!master && (
            <p>Choose a master CV in your files to fill the profile from it.</p>
          )}
          {extracting && (
            <p role="status">Reading your CV. This can take up to a minute…</p>
          )}
        </div>
      )}

      <div className="actions">
        <label>
          Kind{' '}
          <select
            value={kind}
            disabled={upload !== null}
            onChange={(e) => setKind(e.target.value as FileKind)}
          >
            <option value="CV">CV</option>
            <option value="OTHER">Other file</option>
          </select>
        </label>
        <label className="button secondary">
          Upload a file
          <input
            ref={fileInput}
            type="file"
            accept={kind === 'CV' ? ACCEPTED_CV_TYPES : undefined}
            hidden
            disabled={upload !== null}
            onChange={(e) => void onFile(e.target.files?.[0])}
          />
        </label>
      </div>
      {upload && (
        <div className="actions">
          <p role="status">Uploading {upload.name}…</p>
          <progress
            value={upload.fraction}
            max={1}
            aria-label="Upload progress"
          />
          <button
            type="button"
            className="secondary"
            onClick={() => controller.current?.abort()}
          >
            Cancel
          </button>
        </div>
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
