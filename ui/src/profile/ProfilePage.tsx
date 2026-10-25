import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import {
  applyDraft,
  extractSection,
  type ProfileSectionName,
} from '../cv/cvApi.ts'
import { FileCabinet } from '../files/FileCabinet.tsx'
import { loadMaster } from '../files/filesApi.ts'
import { Landing } from '../pages/Landing.tsx'
import { revealAndFocus } from '../ui/revealAndFocus.ts'
import { analyzeProject, type AnalysisResult } from './analysisApi.ts'
import { ProfileForm, type FillState } from './ProfileForm.tsx'
import { listRepositories } from './repositoriesApi.ts'
import { loadProfile, saveProfile } from './profileApi.ts'
import { loadPublicCvState, type PublicCvState } from './publicCvNotice.ts'
import { checkProfile } from './rules.ts'
import { applyAnalysis } from './skills.ts'
import {
  emptyProfile,
  sameRepository,
  toRequestProfile,
  type OpenSourceProject,
  type Profile,
} from './types.ts'

/** The profile page. A visitor without a session sees the landing page and comes back here after signing in. */
export function ProfilePage() {
  const { state } = useAuth()

  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>
    case 'signedOut':
      return <Landing returnTo="/profile" />
    case 'error':
      return (
        <div className="page page--narrow">
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </div>
      )
    case 'signedIn':
      return <ProfileEditor />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready' }

const SECTION_TITLE: Record<ProfileSectionName, string> = {
  experience: 'Experience',
  education: 'Education',
  publications: 'Publications',
}

const NO_FILL: FillState = { loading: false, error: null }

type Save =
  | { state: 'idle' }
  | { state: 'saving' }
  | { state: 'saved' }
  | { state: 'conflict' }
  | { state: 'failed'; message: string }

function snapshot(p: Profile): string {
  return JSON.stringify(toRequestProfile(p))
}

function ProfileEditor() {
  const auth = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const [draft, setDraft] = useState<Profile>(emptyProfile())
  const [version, setVersion] = useState(0)
  // What the server has, in the shape that is sent, to tell whether there are unsaved edits.
  const [saved, setSaved] = useState(snapshot(emptyProfile()))
  // The projects as the server has them; only these can be analysed.
  const [savedProjects, setSavedProjects] = useState<OpenSourceProject[]>([])
  const [save, setSave] = useState<Save>({ state: 'idle' })
  // Server messages from the last failed save, and the paths whose browser-side message is showing.
  const [serverErrors, setErrors] = useState<Record<string, string>>({})
  const [shown, setShown] = useState<ReadonlySet<string>>(new Set())
  // Whether a master CV is chosen. Loaded once here; the cabinet reports the changes it makes.
  const [hasMaster, setHasMaster] = useState(false)
  const [fill, setFill] = useState<Record<ProfileSectionName, FillState>>({
    experience: NO_FILL,
    education: NO_FILL,
    publications: NO_FILL,
  })
  const [notice, setNotice] = useState<string | null>(null)
  // Whether the public CV is live; it only adds the notice, so the page never waits for it.
  const [publicCv, setPublicCv] = useState<PublicCvState>({ state: 'unknown' })
  const formRef = useRef<HTMLFormElement>(null)

  useEffect(() => {
    void loadMaster(auth).then((result) => {
      if (result.kind === 'loaded') setHasMaster(result.hasMaster)
    })
    void loadPublicCvState(auth).then(setPublicCv)
    // Loaded once with the page; `auth` changes with the sign-in state and the cabinet reports later changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const fetchStored = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadProfile(auth)
    if (result.kind === 'loaded') {
      setDraft(result.stored.profile)
      setVersion(result.stored.version)
      setSaved(snapshot(result.stored.profile))
      setSavedProjects(result.stored.profile.openSourceProjects)
      setErrors({})
      setShown(new Set())
      setSave({ state: 'idle' })
      setLoad({ state: 'ready' })
    } else if (result.kind === 'failed') {
      setLoad({ state: 'failed', message: result.message })
    }
    // `unauthorized`: the tokens are gone and the page above starts a new sign-in.
    // `auth` is a new object whenever the sign-in state changes; loading belongs to mount and the Reload button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void fetchStored()
  }, [fetchStored])

  // A message shows once its field was left with a problem, then follows the value as it is typed; the server wins.
  const errors = useMemo(() => {
    const local = checkProfile(draft)
    const visible: Record<string, string> = {}
    for (const path of shown) if (local[path]) visible[path] = local[path]
    return { ...visible, ...serverErrors }
  }, [draft, shown, serverErrors])

  const leave = useCallback(
    (path: string) => {
      if (checkProfile(draft)[path]) setShown((s) => new Set(s).add(path))
    },
    [draft],
  )

  // After a failed save, move to the first field (in page order) that has a message, opening the collapsed sections
  // around it: a field inside a closed `details` cannot take focus.
  const [focusFirst, setFocusFirst] = useState(0)
  useEffect(() => {
    if (focusFirst === 0) return
    const fields = formRef.current?.querySelectorAll<HTMLElement>('[data-path]')
    const first = Array.from(fields ?? []).find(
      (el) => errors[el.dataset['path']!],
    )
    if (!first) return
    revealAndFocus(first)
    first.scrollIntoView?.({ block: 'center' })
    // Only a new failed save moves focus, not later edits.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusFirst])

  const dirty = useMemo(() => snapshot(draft) !== saved, [draft, saved])

  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  function setFillFor(section: ProfileSectionName, state: FillState) {
    setFill((current) => ({ ...current, [section]: state }))
  }

  /**
   * Replaces only this section's entries with the draft from the CV, as unsaved changes. A section with entries asks
   * first; declining sends nothing.
   */
  async function onFillSection(section: ProfileSectionName) {
    const title = SECTION_TITLE[section]
    if (
      draft[section].length > 0 &&
      !window.confirm(
        `Replace the ${title} entries in the form with the draft from your CV?`,
      )
    )
      return
    setFillFor(section, { loading: true, error: null })
    setNotice(null)
    const result = await extractSection(auth, section)
    if (result.kind === 'draft') {
      const entries = result.profile[section]
      setDraft((current) => ({ ...current, [section]: entries }))
      setFillFor(section, NO_FILL)
      setNotice(
        `The ${title} entries now hold a draft from your CV. Nothing is saved until you press Save. Check every entry first.`,
      )
    } else if (result.kind === 'failed') {
      setFillFor(section, { loading: false, error: result.message })
    } else {
      setFillFor(section, NO_FILL)
    }
  }

  async function onSave() {
    setSave({ state: 'saving' })
    setErrors({})
    const result = await saveProfile(auth, version, draft)
    switch (result.kind) {
      case 'saved':
        setVersion(result.stored.version)
        setSaved(snapshot(draft))
        setSavedProjects(result.stored.profile.openSourceProjects)
        setSave({ state: 'saved' })
        break
      case 'invalid':
        setErrors(result.fields)
        setFocusFirst((n) => n + 1)
        setSave({ state: 'failed', message: 'Please fix the marked fields.' })
        break
      case 'conflict':
        setSave({ state: 'conflict' })
        break
      case 'failed':
        setSave({ state: 'failed', message: result.message })
        break
      case 'unauthorized':
        break
    }
  }

  /**
   * Analyses a saved project. A success puts the analysis on the project and changes the skills, as unsaved changes;
   * a failure changes nothing here (the section shows its message).
   */
  async function onAnalyze(
    project: OpenSourceProject,
  ): Promise<AnalysisResult> {
    const result = await analyzeProject(
      auth,
      project.provider,
      project.fullName,
    )
    if (result.kind === 'analyzed') {
      const analysis = result.analysis
      setDraft((current) => {
        const picked = current.openSourceProjects.find((p) =>
          sameRepository(p, project),
        )
        // The project may have been removed while the request ran; then nothing is added.
        if (!picked) return current
        return {
          ...current,
          openSourceProjects: current.openSourceProjects.map((p) =>
            p === picked ? { ...p, analysis } : p,
          ),
          skills: applyAnalysis(current.skills, picked, analysis),
        }
      })
      setNotice(
        `${project.name} was analyzed. Its technologies are in Skills. Nothing is saved until you press Save.`,
      )
    }
    return result
  }

  if (load.state === 'loading') return <p>Loading your profile…</p>
  if (load.state === 'failed') {
    return (
      <div className="page page--narrow">
        <h1>Your profile</h1>
        <p role="alert">{load.message}</p>
        <button type="button" onClick={() => void fetchStored()}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="page page--narrow">
      <p>
        <Link
          to="/"
          onClick={(e) => {
            if (dirty && !window.confirm('Leave without saving your changes?'))
              e.preventDefault()
          }}
        >
          Back
        </Link>
      </p>
      <h1>Your profile</h1>
      <p>
        These facts are the only source for the applications we draft for you.
      </p>
      <FileCabinet
        formHasContent={snapshot(draft) !== snapshot(emptyProfile())}
        onDraft={(d) => setDraft((current) => applyDraft(current, d))}
        onMasterChange={setHasMaster}
      />
      <form
        ref={formRef}
        onSubmit={(e) => {
          e.preventDefault()
          void onSave()
        }}
        noValidate
      >
        {notice && (
          <p className="notice" role="status">
            {notice}
          </p>
        )}
        <ProfileForm
          profile={draft}
          onChange={setDraft}
          errors={errors}
          onLeave={leave}
          canFillFromCv={hasMaster}
          onFillSection={(section) => void onFillSection(section)}
          fillState={fill}
          onLoadRepositories={(provider, username) =>
            listRepositories(auth, provider, username)
          }
          savedProjects={savedProjects}
          onAnalyze={onAnalyze}
          publicCv={publicCv}
        />

        {save.state === 'conflict' && (
          <div className="notice" role="alert">
            <p>
              Your profile was changed somewhere else. Your edits are still
              here; saving is paused.
            </p>
            <button
              type="button"
              className="secondary"
              onClick={() => void fetchStored()}
            >
              Reload
            </button>
          </div>
        )}
        {save.state === 'failed' && <p role="alert">{save.message}</p>}
        {save.state === 'saved' && !dirty && (
          <p role="status">{`Saved (version ${version}).`}</p>
        )}
        <div className="actions">
          <button
            type="submit"
            disabled={save.state === 'saving' || save.state === 'conflict'}
          >
            {save.state === 'saving' ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </div>
  )
}
