import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Landing } from '../pages/Landing.tsx'
import { ProfileForm } from './ProfileForm.tsx'
import { loadProfile, saveProfile } from './profileApi.ts'
import { checkProfile } from './rules.ts'
import { emptyProfile, toRequestProfile, type Profile } from './types.ts'

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
        <main>
          <h1>Sign-in failed</h1>
          <p>We could not reach the sign-in service.</p>
        </main>
      )
    case 'signedIn':
      return <ProfileEditor />
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'failed'; message: string }
  | { state: 'ready' }

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
  const [save, setSave] = useState<Save>({ state: 'idle' })
  // Server messages from the last failed save, and the paths whose browser-side message is showing.
  const [serverErrors, setErrors] = useState<Record<string, string>>({})
  const [shown, setShown] = useState<ReadonlySet<string>>(new Set())
  const formRef = useRef<HTMLFormElement>(null)

  const fetchStored = useCallback(async () => {
    setLoad({ state: 'loading' })
    const result = await loadProfile(auth)
    if (result.kind === 'loaded') {
      setDraft(result.stored.profile)
      setVersion(result.stored.version)
      setSaved(snapshot(result.stored.profile))
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

  // After a failed save, move to the first field (in page order) that has a message.
  const [focusFirst, setFocusFirst] = useState(0)
  useEffect(() => {
    if (focusFirst === 0) return
    const fields = formRef.current?.querySelectorAll<HTMLElement>('[data-path]')
    const first = Array.from(fields ?? []).find(
      (el) => errors[el.dataset['path']!],
    )
    first?.focus()
    first?.scrollIntoView?.({ block: 'center' })
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

  async function onSave() {
    setSave({ state: 'saving' })
    setErrors({})
    const result = await saveProfile(auth, version, draft)
    switch (result.kind) {
      case 'saved':
        setVersion(result.stored.version)
        setSaved(snapshot(draft))
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

  if (load.state === 'loading') return <p>Loading your profile…</p>
  if (load.state === 'failed') {
    return (
      <main>
        <h1>Your profile</h1>
        <p role="alert">{load.message}</p>
        <button type="button" onClick={() => void fetchStored()}>
          Try again
        </button>
      </main>
    )
  }

  return (
    <main className="wide">
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
      <form
        ref={formRef}
        onSubmit={(e) => {
          e.preventDefault()
          void onSave()
        }}
        noValidate
      >
        <ProfileForm
          profile={draft}
          onChange={setDraft}
          errors={errors}
          onLeave={leave}
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
    </main>
  )
}
