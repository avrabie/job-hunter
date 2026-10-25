import { useEffect, useState, type FormEvent } from 'react'
import { PageLoading } from '../ui/PageLoading.tsx'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import '../styles/publiccv.css'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import { PageHeader } from '../ui/PageHeader.tsx'
import { JpegPhotoPicker } from '../files/JpegPhotoPicker.tsx'
import { listFiles, type FileInfo } from '../files/filesApi.ts'
import {
  checkHandle,
  getPublicCv,
  handleReasonMessage,
  previewPublicCv,
  removePublicCv,
  savePublicCv,
  type HandleCheck,
  type PublicCvErrorCode,
  type PublicCvResult,
  type PublicCvState,
} from './publicCvApi.ts'
import {
  PUBLIC_CV_PATH,
  PUBLIC_SECTION_KINDS,
  PUBLIC_SECTION_TITLES,
  defaultSettings,
  handleProblem,
  moveSection,
  setSection,
  showsPublicPhotoPicker,
  type PublicCvContact,
  type PublicCvLanguage,
  type PublicCvSettings,
  type PublicCvTemplate,
} from './options.ts'

// The availability check waits this long after the last keystroke, so a burst of typing makes one request.
const HANDLE_DELAY_MS = 400

/** The public CV page. A visitor without a session sees the landing page. */
export function PublicCvPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <PageLoading title="Public CV" />
    case 'signedOut':
      return <Landing returnTo="/publish" />
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
      return <PublicCvForm />
  }
}

/** The answer for the handle as it was when the check was sent. */
interface CheckView {
  handle: string
  text: string
  available: boolean
}

interface Notice {
  tone: 'status' | 'alert'
  text: string
  code: PublicCvErrorCode | null
}

type Busy = 'save' | 'remove' | 'preview' | null

/** The cabinet files for the photo picker; loaded only while the picker shows. */
type Photos =
  | { state: 'loading' }
  | { state: 'loaded'; files: FileInfo[] }
  | { state: 'failed'; message: string }

function PublicCvForm() {
  const auth = useAuth()
  // The clock is read once, when the page opens: "can change from" is compared with that moment.
  const [startedAt] = useState(() => Date.now())
  const [saved, setSaved] = useState<PublicCvState>({ configured: false })
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [handle, setHandle] = useState('')
  const [settings, setSettings] = useState<PublicCvSettings>(defaultSettings)
  const [handleError, setHandleError] = useState<string | null>(null)
  const [check, setCheck] = useState<CheckView | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [busy, setBusy] = useState<Busy>(null)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>(
    'idle',
  )
  const [photos, setPhotos] = useState<Photos>({ state: 'loading' })
  const photoPicker = showsPublicPhotoPicker(settings)

  // What the server holds. The form starts from it and keeps what the user types until a save or a removal.
  const savedHandle = saved.configured ? saved.handle : null
  const live = saved.configured && saved.published
  const locked = saved.configured && saved.locked
  const changeableAt = saved.configured ? saved.handleChangeableAt : null
  const handleLocked =
    changeableAt !== null && Date.parse(changeableAt) > startedAt
  const liveAddress = savedHandle === null ? null : addressOf(savedHandle)

  useEffect(() => {
    let active = true
    getPublicCv(auth).then((result) => {
      if (!active) return
      if (result.kind === 'ok') {
        if (result.value.configured) {
          setHandle(result.value.handle)
          setSettings(result.value.settings)
        }
        setSaved(result.value)
        setLoaded(true)
      } else if (result.kind === 'failed') {
        setLoadError(result.message)
      }
    })
    return () => {
      active = false
    }
  }, [auth])

  useEffect(() => {
    if (!photoPicker) return
    let ignore = false
    void listFiles(auth).then((result) => {
      if (ignore) return
      if (result.kind === 'loaded')
        setPhotos({ state: 'loaded', files: result.files })
      else if (result.kind === 'failed')
        setPhotos({ state: 'failed', message: result.message })
      else setPhotos({ state: 'failed', message: 'Please sign in again.' })
    })
    return () => {
      ignore = true
    }
  }, [auth, photoPicker])

  // The availability check: only a well-formed handle that is not the saved one is looked up, once the user pauses.
  useEffect(() => {
    if (handle === '' || handleProblem(handle) !== null) return
    if (handle === savedHandle) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      checkHandle(auth, handle, controller.signal).then((result) => {
        if (controller.signal.aborted) return
        const view = checkView(handle, result)
        if (view) setCheck(view)
      })
    }, HANDLE_DELAY_MS)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [auth, handle, savedHandle])

  const update = (change: Partial<PublicCvSettings>) =>
    setSettings((current) => ({ ...current, ...change }))

  const setContact = (key: keyof PublicCvContact, on: boolean) =>
    setSettings((current) => ({
      ...current,
      contact: { ...current.contact, [key]: on },
    }))

  const onHandleChange = (value: string) => {
    setHandle(value.toLowerCase())
    setHandleError(null)
  }

  const save = async (published: boolean) => {
    const problem = handleProblem(handle)
    if (problem !== null) {
      setHandleError(problem)
      return
    }
    setBusy('save')
    setNotice(null)
    const result = await savePublicCv(auth, { handle, published, settings })
    setBusy(null)
    if (result.kind === 'unauthorized') return
    if (result.kind === 'failed') {
      showFailure(result.code, result.message)
      return
    }
    setSaved(result.value)
    setNotice({
      tone: 'status',
      text: published ? 'Published.' : 'Saved.',
      code: null,
    })
  }

  const showFailure = (code: PublicCvErrorCode | null, message: string) => {
    if (
      code === 'handle_taken' ||
      code === 'handle_reserved' ||
      code === 'handle_held' ||
      code === 'handle_change_too_soon' ||
      code === 'validation_failed'
    ) {
      setHandleError(message)
      setNotice(null)
      return
    }
    if (code === 'locked') {
      setSaved((current) =>
        current.configured ? { ...current, locked: true } : current,
      )
    }
    setNotice({ tone: 'alert', text: message, code })
  }

  const remove = async () => {
    setConfirmRemove(false)
    setBusy('remove')
    setNotice(null)
    const result = await removePublicCv(auth)
    setBusy(null)
    if (result.kind === 'unauthorized') return
    if (result.kind === 'failed') {
      setNotice({ tone: 'alert', text: result.message, code: result.code })
      return
    }
    setSaved({ configured: false })
    setHandle('')
    setSettings(defaultSettings())
    setHandleError(null)
    setCheck(null)
    setNotice({
      tone: 'status',
      text: 'Your public CV was removed.',
      code: null,
    })
  }

  const preview = async () => {
    setBusy('preview')
    setPreviewError(null)
    const result = await previewPublicCv(auth, settings)
    setBusy(null)
    if (result.kind === 'ok') {
      window.open(result.value.path, '_blank', 'noopener')
    } else if (result.kind === 'failed') {
      setPreviewError(result.message)
    }
  }

  const copyLink = async () => {
    if (liveAddress === null) return
    try {
      await navigator.clipboard.writeText(liveAddress)
      setCopyState('copied')
    } catch {
      setCopyState('failed')
    }
  }

  if (loadError !== null) {
    return (
      <div className="page page--narrow">
        <PageHeader title="Public CV" />
        <Alert tone="danger">{loadError}</Alert>
      </div>
    )
  }
  if (!loaded) {
    return (
      <div className="page page--narrow">
        <PageHeader title="Public CV" />
        <p role="status">Loading…</p>
      </div>
    )
  }

  const rows = [
    ...settings.sections,
    ...PUBLIC_SECTION_KINDS.filter((kind) => !settings.sections.includes(kind)),
  ]
  const handleText = handleMessage(handle, savedHandle, handleError, check)
  const busyNow = busy !== null

  return (
    <div className="page page--narrow">
      <PageHeader title="Public CV" />
      <p>
        <Link to="/">Back</Link>
      </p>
      <p>
        Your full name is always shown. Preferences are never shown. Every other
        field is shown only where you tick it below.
      </p>

      <Card title="Status">
        {live && liveAddress !== null ? (
          <Alert tone="success">
            <p>
              Your CV is live at <code>{liveAddress}</code>. Every change you
              save in your profile shows there at once, in the fields ticked
              above.
            </p>
            <button
              type="button"
              className="button--secondary"
              onClick={() => void copyLink()}
            >
              {copyState === 'copied'
                ? 'Copied'
                : copyState === 'failed'
                  ? 'Could not copy'
                  : 'Copy link'}
            </button>
          </Alert>
        ) : (
          <Alert tone="info">Your CV is not public.</Alert>
        )}
        {locked && (
          <Alert tone="danger">
            An administrator disabled this page. Publishing is not possible.
          </Alert>
        )}
      </Card>

      <form className="public-cv-form" onSubmit={noSubmit}>
        <Card title="Address">
          <label htmlFor="public-handle">Handle</label>
          <input
            id="public-handle"
            type="text"
            autoComplete="off"
            spellCheck={false}
            value={handle}
            disabled={handleLocked}
            aria-describedby={handleText ? 'public-handle-message' : undefined}
            onChange={(e) => onHandleChange(e.target.value)}
          />
          {handleText && (
            <p
              id="public-handle-message"
              role={handleText.error ? 'alert' : 'status'}
            >
              {handleText.text}
            </p>
          )}
          {handle !== '' && (
            <p>
              Your page will be at{' '}
              <code className="public-cv-address">{addressOf(handle)}</code>
            </p>
          )}
          {handleLocked && changeableAt !== null && (
            <p>You can change the handle from {formatDay(changeableAt)}.</p>
          )}
        </Card>

        <Card title="Layout">
          <FormField label="Template">
            {(control) => (
              <select
                {...control}
                value={settings.template}
                onChange={(e) =>
                  update({ template: e.target.value as PublicCvTemplate })
                }
              >
                <option value="CLASSIC">Classic</option>
                <option value="MODERN">Modern</option>
                <option value="COMPACT">Compact</option>
                <option value="PROFESSIONAL">Professional</option>
              </select>
            )}
          </FormField>
          {photoPicker && (
            <div>
              {photos.state === 'loading' && (
                <p role="status">Loading your photos…</p>
              )}
              {photos.state === 'failed' && (
                <Alert tone="danger">{photos.message}</Alert>
              )}
              {photos.state === 'loaded' && (
                <JpegPhotoPicker
                  id="public-photo"
                  files={photos.files}
                  selected={settings.photoFileId}
                  onChange={(photoFileId) => update({ photoFileId })}
                />
              )}
              <p className="form-field__hint">
                The photo becomes visible to everyone with your link once you
                publish.
              </p>
            </div>
          )}
          <FormField label="Language">
            {(control) => (
              <select
                {...control}
                value={settings.language}
                onChange={(e) =>
                  update({ language: e.target.value as PublicCvLanguage })
                }
              >
                <option value="EN">English</option>
                <option value="DE">Deutsch</option>
              </select>
            )}
          </FormField>
        </Card>

        <fieldset>
          <legend>Sections</legend>
          <ol className="public-cv-sections">
            {rows.map((kind) => {
              const title = PUBLIC_SECTION_TITLES[kind]
              const index = settings.sections.indexOf(kind)
              const ticked = index !== -1
              return (
                <li key={kind}>
                  <label className="inline">
                    <input
                      type="checkbox"
                      checked={ticked}
                      onChange={(e) =>
                        update({
                          sections: setSection(
                            settings.sections,
                            kind,
                            e.target.checked,
                          ),
                        })
                      }
                    />
                    {title}
                  </label>
                  {ticked && (
                    <>
                      <button
                        type="button"
                        className="button--secondary"
                        aria-label={`Move ${title} up`}
                        disabled={index === 0}
                        onClick={() =>
                          update({
                            sections: moveSection(settings.sections, index, -1),
                          })
                        }
                      >
                        Up
                      </button>
                      <button
                        type="button"
                        className="button--secondary"
                        aria-label={`Move ${title} down`}
                        disabled={index === settings.sections.length - 1}
                        onClick={() =>
                          update({
                            sections: moveSection(settings.sections, index, 1),
                          })
                        }
                      >
                        Down
                      </button>
                    </>
                  )}
                </li>
              )
            })}
          </ol>
        </fieldset>

        <fieldset>
          <legend>Details</legend>
          <CheckBox
            label="Headline"
            checked={settings.headline}
            onChange={(on) => update({ headline: on })}
          />
          <CheckBox
            label="Analysis summaries"
            checked={settings.analysis}
            onChange={(on) => update({ analysis: on })}
          />
          <CheckBox
            label="Work authorisation"
            checked={settings.workAuthorisation}
            onChange={(on) => update({ workAuthorisation: on })}
          />
          <CheckBox
            label="Location"
            checked={settings.location}
            onChange={(on) => update({ location: on })}
          />
        </fieldset>

        <fieldset>
          <legend>Contact</legend>
          <CheckBox
            label="Email"
            checked={settings.contact.email}
            onChange={(on) => setContact('email', on)}
          />
          <CheckBox
            label="Phone"
            checked={settings.contact.phone}
            onChange={(on) => setContact('phone', on)}
          />
          <CheckBox
            label="Website"
            checked={settings.contact.website}
            onChange={(on) => setContact('website', on)}
          />
          <CheckBox
            label="LinkedIn"
            checked={settings.contact.linkedin}
            onChange={(on) => setContact('linkedin', on)}
          />
          <CheckBox
            label="Code-account links"
            checked={settings.contact.codeAccounts}
            onChange={(on) => setContact('codeAccounts', on)}
          />
        </fieldset>

        <fieldset>
          <legend>Search engines</legend>
          <CheckBox
            label="Allow search engines"
            checked={settings.indexable}
            onChange={(on) => update({ indexable: on })}
          />
        </fieldset>

        <div className="public-cv-actions">
          {live ? (
            <button
              type="button"
              className="button--secondary"
              disabled={busyNow}
              onClick={() => void save(false)}
            >
              Pause
            </button>
          ) : (
            <button
              type="button"
              disabled={busyNow || locked}
              onClick={() => void save(true)}
            >
              Publish
            </button>
          )}
          <button
            type="button"
            className="button--secondary"
            disabled={busyNow}
            onClick={() => void save(live)}
          >
            Save
          </button>
          <button
            type="button"
            className="button--secondary"
            disabled={busyNow}
            onClick={() => void preview()}
          >
            Preview
          </button>
          {saved.configured && (
            <button
              type="button"
              className="button--secondary"
              disabled={busyNow}
              onClick={() => setConfirmRemove(true)}
            >
              Remove
            </button>
          )}
        </div>
      </form>

      <p>
        The preview opens in a new tab. It is made from your saved profile and
        the settings on this page.
      </p>
      {busy === 'preview' && <p role="status">Preparing the preview…</p>}
      {previewError !== null && <Alert tone="danger">{previewError}</Alert>}

      {confirmRemove && (
        <Card title="Remove the public CV?">
          <p>
            The address stops working. The handle is kept for you for 90 days.
          </p>
          <div className="public-cv-actions">
            <button type="button" onClick={() => void remove()}>
              Remove the public CV
            </button>
            <button
              type="button"
              className="button--secondary"
              onClick={() => setConfirmRemove(false)}
            >
              Keep it
            </button>
          </div>
        </Card>
      )}

      {notice !== null && (
        <Alert tone={notice.tone === 'alert' ? 'danger' : 'success'}>
          {notice.text}
          {notice.code === 'name_required' && (
            <>
              {' '}
              <Link to="/profile#contact">Go to the Contact section</Link>
            </>
          )}
        </Alert>
      )}
    </div>
  )
}

function CheckBox({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (on: boolean) => void
}) {
  return (
    <label className="inline">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  )
}

/** The text under the handle field and whether it reports a problem. */
function handleMessage(
  handle: string,
  savedHandle: string | null,
  handleError: string | null,
  check: CheckView | null,
): { text: string; error: boolean } | null {
  if (handleError !== null) return { text: handleError, error: true }
  if (handle === '') return null
  const problem = handleProblem(handle)
  if (problem !== null) return { text: problem, error: true }
  if (handle === savedHandle)
    return { text: 'This is your current handle.', error: false }
  if (check !== null && check.handle === handle)
    return { text: check.text, error: !check.available }
  return null
}

function checkView(
  handle: string,
  result: PublicCvResult<HandleCheck>,
): CheckView | null {
  if (result.kind === 'unauthorized') return null
  if (result.kind === 'failed')
    return { handle, text: result.message, available: false }
  if (result.value.available)
    return { handle, text: 'Available.', available: true }
  return {
    handle,
    text: handleReasonMessage(result.value.reason),
    available: false,
  }
}

/** The address a page has under this handle, built from the origin and the one path constant. */
function addressOf(handle: string): string {
  return `${window.location.origin}${PUBLIC_CV_PATH}${handle}`
}

function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

function noSubmit(e: FormEvent<HTMLFormElement>) {
  e.preventDefault()
}
