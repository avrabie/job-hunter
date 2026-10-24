import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { messageForError } from '../api/messages.ts'
import { useAuth, type AuthApi } from '../auth/authContext.ts'
import { JpegPhotoPicker } from '../files/JpegPhotoPicker.tsx'
import { listFiles, type FileInfo } from '../files/filesApi.ts'
import { Landing } from '../pages/Landing.tsx'
import { exportCv, type ExportErrorCode } from './exportApi.ts'
import {
  SECTION_TITLES,
  defaultOptions,
  moveSection,
  showsPhotoPicker,
  type CvFormat,
  type CvLanguage,
  type CvSectionKind,
  type CvTemplate,
  type ExportOptions,
} from './options.ts'

// The preview waits this long after the last change, so a burst of changes makes one request.
const PREVIEW_DELAY_MS = 250

/** The CV export page. A visitor without a session sees the landing page. */
export function ExportPage() {
  const { state } = useAuth()
  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/export" />
    case 'error':
      return (
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </main>
      )
    case 'signedIn':
      return <ExportForm />
  }
}

/** The answer for the options with this `key`; while the key differs from the current options the preview is loading. */
type Preview =
  | { key: string; state: 'ready'; html: string }
  | {
      key: string
      state: 'failed'
      code: ExportErrorCode | null
      message: string
    }

type Download =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'failed'; code: ExportErrorCode | null; message: string }

/** The cabinet files for the photo picker; loaded only while the picker shows. */
type Photos =
  | { state: 'loading' }
  | { state: 'loaded'; files: FileInfo[] }
  | { state: 'failed'; message: string }

function ExportForm() {
  const auth = useAuth()
  const [options, setOptions] = useState<ExportOptions>(defaultOptions)
  const [answer, setAnswer] = useState<Preview | null>(null)
  const [download, setDownload] = useState<Download>({ state: 'idle' })
  const [photos, setPhotos] = useState<Photos>({ state: 'loading' })
  const key = JSON.stringify(options)
  const preview: Preview | null = answer?.key === key ? answer : null
  const photoPicker = showsPhotoPicker(options)

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

  useEffect(() => {
    // Each change cancels the pending request or the one in flight, so at most one is left running.
    const controller = new AbortController()
    const asked: ExportOptions = JSON.parse(key) as ExportOptions
    const timer = window.setTimeout(() => {
      previewOf(auth, { ...asked, format: 'HTML' }, controller.signal, key)
        .then((next) => {
          if (next && !controller.signal.aborted) setAnswer(next)
        })
        .catch((e: unknown) => {
          if (!controller.signal.aborted)
            setAnswer({
              key,
              state: 'failed',
              code: null,
              message: messageForError(e),
            })
        })
    }, PREVIEW_DELAY_MS)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [auth, key])

  const update = (change: Partial<ExportOptions>) =>
    setOptions((current) => ({ ...current, ...change }))

  const setSection = (kind: CvSectionKind, included: boolean) =>
    update({
      sections: options.sections.map((s) =>
        s.kind === kind ? { ...s, included } : s,
      ),
    })

  const onDownload = async () => {
    setDownload({ state: 'loading' })
    const result = await exportCv(auth, options)
    if (result.kind === 'exported') {
      saveFile(result.blob, result.fileName)
      setDownload({ state: 'idle' })
    } else if (result.kind === 'failed') {
      setDownload({
        state: 'failed',
        code: result.code,
        message: result.message,
      })
    } else {
      setDownload({ state: 'idle' })
    }
  }

  return (
    <main className="wide">
      <h1>Export CV</h1>
      <p>
        <Link to="/">Back</Link>
      </p>
      <div className="export-layout">
        <form className="export-options" onSubmit={noSubmit}>
          <div className="export-field">
            <label htmlFor="export-format">Format</label>
            <select
              id="export-format"
              value={options.format}
              onChange={(e) => update({ format: e.target.value as CvFormat })}
            >
              <option value="PDF">PDF</option>
              <option value="MARKDOWN">Markdown</option>
              <option value="HTML">HTML</option>
            </select>
          </div>
          <div className="export-field">
            <label htmlFor="export-template">Template</label>
            <select
              id="export-template"
              value={options.template}
              onChange={(e) =>
                update({ template: e.target.value as CvTemplate })
              }
            >
              <option value="CLASSIC">Classic</option>
              <option value="MODERN">Modern</option>
              <option value="COMPACT">Compact</option>
              <option value="PROFESSIONAL">Professional</option>
            </select>
          </div>
          {photoPicker && (
            <div className="export-field">
              {photos.state === 'loading' && (
                <p role="status">Loading your photos…</p>
              )}
              {photos.state === 'failed' && (
                <p role="alert">{photos.message}</p>
              )}
              {photos.state === 'loaded' && (
                <JpegPhotoPicker
                  id="export-photo"
                  files={photos.files}
                  selected={options.photoFileId}
                  onChange={(photoFileId) => update({ photoFileId })}
                />
              )}
            </div>
          )}
          <div className="export-field">
            <label htmlFor="export-language">Language</label>
            <select
              id="export-language"
              value={options.language}
              onChange={(e) =>
                update({ language: e.target.value as CvLanguage })
              }
            >
              <option value="EN">English</option>
              <option value="DE">Deutsch</option>
            </select>
          </div>

          <fieldset>
            <legend>Sections</legend>
            <ol className="export-sections">
              {options.sections.map((choice, index) => {
                const title = SECTION_TITLES[choice.kind]
                return (
                  <li key={choice.kind}>
                    <label className="inline">
                      <input
                        type="checkbox"
                        checked={choice.included}
                        onChange={(e) =>
                          setSection(choice.kind, e.target.checked)
                        }
                      />
                      {title}
                    </label>
                    <button
                      type="button"
                      className="secondary"
                      aria-label={`Move ${title} up`}
                      disabled={index === 0}
                      onClick={() =>
                        update({
                          sections: moveSection(options.sections, index, -1),
                        })
                      }
                    >
                      Up
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      aria-label={`Move ${title} down`}
                      disabled={index === options.sections.length - 1}
                      onClick={() =>
                        update({
                          sections: moveSection(options.sections, index, 1),
                        })
                      }
                    >
                      Down
                    </button>
                  </li>
                )
              })}
            </ol>
          </fieldset>

          <fieldset>
            <legend>Options</legend>
            <label className="inline">
              <input
                type="checkbox"
                checked={options.includeOpenSource}
                onChange={(e) =>
                  update({ includeOpenSource: e.target.checked })
                }
              />
              Open source projects
            </label>
            <label className="inline">
              <input
                type="checkbox"
                checked={options.includeAnalysis}
                disabled={!options.includeOpenSource}
                onChange={(e) => update({ includeAnalysis: e.target.checked })}
              />
              Analysis summaries
            </label>
            <label className="inline">
              <input
                type="checkbox"
                checked={options.includeWorkAuthorisation}
                onChange={(e) =>
                  update({ includeWorkAuthorisation: e.target.checked })
                }
              />
              Work authorisation
            </label>
          </fieldset>
        </form>

        <div className="export-side">
          <section aria-labelledby="export-preview-heading">
            <h2 id="export-preview-heading">Preview</h2>
            {preview === null && <p role="status">Loading preview…</p>}
            {preview?.state === 'failed' && (
              <Failure code={preview.code} message={preview.message} />
            )}
            <iframe
              title="CV preview"
              sandbox=""
              srcDoc={preview?.state === 'ready' ? preview.html : ''}
              className="export-preview"
            />
          </section>

          <section aria-labelledby="export-download-heading">
            <h2 id="export-download-heading">Download</h2>
            <button
              type="button"
              disabled={download.state === 'loading'}
              onClick={() => void onDownload()}
            >
              Download
            </button>
            {download.state === 'loading' && (
              <p role="status">Preparing the file…</p>
            )}
            {download.state === 'failed' && (
              <Failure code={download.code} message={download.message} />
            )}
          </section>
        </div>
      </div>
    </main>
  )
}

/** The fixed message, with a way to the Contact section when the name is missing. */
function Failure({
  code,
  message,
}: {
  code: ExportErrorCode | null
  message: string
}) {
  return (
    <p role="alert">
      {message}
      {code === 'name_required' && (
        <>
          {' '}
          <Link to="/profile#contact">Go to the Contact section</Link>
        </>
      )}
    </p>
  )
}

/** Null when the sign-in ended: the page then shows the landing page and there is no preview to keep. */
async function previewOf(
  auth: AuthApi,
  options: ExportOptions,
  signal: AbortSignal,
  key: string,
): Promise<Preview | null> {
  const result = await exportCv(auth, options, signal)
  if (result.kind === 'exported') {
    return { key, state: 'ready', html: await result.blob.text() }
  }
  if (result.kind === 'failed') {
    return {
      key,
      state: 'failed',
      code: result.code,
      message: result.message,
    }
  }
  return null
}

/** Saves the file through an object URL that is revoked afterwards. Nothing is kept in the page or in storage. */
function saveFile(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function noSubmit(e: FormEvent<HTMLFormElement>) {
  e.preventDefault()
}
