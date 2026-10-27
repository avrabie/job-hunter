import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import { loadProfile } from '../profile/profileApi.ts'
import type { Profile } from '../profile/types.ts'
import { Alert } from '../ui/Alert.tsx'
import { Card } from '../ui/Card.tsx'
import { FormField } from '../ui/FormField.tsx'
import '../styles/applications.css'
import {
  deleteLetter,
  downloadLetter,
  downloadLetterPdf,
  generateLetter,
  listLetters,
  readLetter,
  saveEditedLetter,
  type GenerateRequest,
  type LetterDetail,
  type LetterLanguage,
  type LetterResult,
  type LetterSummary,
} from './lettersApi.ts'
import { paragraphs } from './paragraphs.ts'
import {
  analyseSkills,
  readSkills,
  saveSkillNotes,
  type SkillsResult,
} from './skillsApi.ts'
import { SkillsStep, type Analysed } from './SkillsStep.tsx'

export const CHECK_NOTICE =
  'Check every statement before you send this letter; the writing assistant can get facts wrong.'

const MAX_EXPERIENCE = 8
const MAX_PROJECTS = 6
const MAX_SKILLS = 40
const SIGN_IN_AGAIN = 'Please sign in again.'

type ProfileState =
  | { state: 'loading' }
  | { state: 'loaded'; profile: Profile }
  | { state: 'failed'; message: string }

interface Selection {
  experience: number[]
  projects: number[]
  skills: number[]
}

const toggled = (list: number[], index: number, on: boolean) =>
  on ? [...list, index].sort((a, b) => a - b) : list.filter((i) => i !== index)

/** The letter workspace of one application: choose facts, generate, review, comment, regenerate, edit, copy, download. */
export function LetterWorkspace({ applicationId }: { applicationId: string }) {
  const auth = useAuth()
  const [profile, setProfile] = useState<ProfileState>({ state: 'loading' })
  const [versions, setVersions] = useState<LetterSummary[]>([])
  const [current, setCurrent] = useState<LetterDetail | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [language, setLanguage] = useState<LetterLanguage | ''>('')
  const [instructions, setInstructions] = useState('')
  const [selection, setSelection] = useState<Selection>({
    experience: [],
    projects: [],
    skills: [],
  })
  const [general, setGeneral] = useState('')
  const [perParagraph, setPerParagraph] = useState<Record<number, string>>({})
  const [editing, setEditing] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [analysis, setAnalysis] = useState<Analysed | null>(null)
  const [skillsLoading, setSkillsLoading] = useState(true)
  const [skillsMessage, setSkillsMessage] = useState<string | null>(null)
  const [skillsStatus, setSkillsStatus] = useState<string | null>(null)
  const [ticked, setTicked] = useState<number[]>([])
  const [tickedEvidenced, setTickedEvidenced] = useState<string[]>([])
  const [notes, setNotes] = useState<Record<string, string>>({})

  /** Shows an analysis: nothing ticked, notes as saved. */
  const applyAnalysis = useCallback((result: SkillsResult) => {
    if (result.kind === 'done' && result.analysis.analysed) {
      const a = result.analysis
      setAnalysis(a)
      setTicked([])
      setTickedEvidenced([])
      setNotes(Object.fromEntries(a.gaps.map((g) => [g.name, g.note ?? ''])))
    } else if (result.kind === 'done') {
      setAnalysis(null)
    }
  }, [])

  const show = useCallback((letter: LetterDetail) => {
    setCurrent(letter)
    setGeneral('')
    setPerParagraph({})
    setEditing(null)
    setSelection({
      experience: letter.selection.experience,
      projects: letter.selection.projects,
      skills: letter.selection.skills,
    })
    setLanguage(letter.language)
    setInstructions(letter.instructions ?? '')
  }, [])

  useEffect(() => {
    let ignore = false
    void loadProfile(auth).then((result) => {
      if (ignore) return
      if (result.kind === 'loaded')
        setProfile({ state: 'loaded', profile: result.stored.profile })
      else
        setProfile({
          state: 'failed',
          message: result.kind === 'failed' ? result.message : SIGN_IN_AGAIN,
        })
    })
    void readSkills(auth, applicationId).then((result) => {
      if (ignore) return
      // A failed read is not an error: the step stays optional.
      applyAnalysis(result)
      setSkillsLoading(false)
    })
    void (async () => {
      const list = await listLetters(auth, applicationId)
      if (ignore) return
      if (list.kind !== 'loaded') {
        setMessage(list.kind === 'failed' ? list.message : SIGN_IN_AGAIN)
        setLoaded(true)
        return
      }
      setVersions(list.letters)
      const newest = list.letters.reduce<number | null>(
        (best, l) => (best === null || l.version > best ? l.version : best),
        null,
      )
      if (newest !== null) {
        const read = await readLetter(auth, applicationId, newest)
        if (ignore) return
        if (read.kind === 'done') show(read.letter)
        else setMessage(read.kind === 'failed' ? read.message : SIGN_IN_AGAIN)
      }
      setLoaded(true)
    })()
    return () => {
      ignore = true
    }
  }, [auth, applicationId, show, applyAnalysis])

  /** Runs one call that makes a new version, then shows it and refreshes the list. */
  const makeVersion = async (call: () => Promise<LetterResult>) => {
    setBusy(true)
    setMessage(null)
    setStatus(null)
    const result = await call()
    if (result.kind === 'done') {
      show(result.letter)
      const list = await listLetters(auth, applicationId)
      if (list.kind === 'loaded') setVersions(list.letters)
    } else {
      setMessage(result.kind === 'failed' ? result.message : SIGN_IN_AGAIN)
    }
    setBusy(false)
  }

  const runSkills = async (
    call: () => Promise<SkillsResult>,
    done: string | null,
  ) => {
    setBusy(true)
    setSkillsMessage(null)
    setSkillsStatus(null)
    const result = await call()
    if (result.kind === 'done') {
      applyAnalysis(result)
      setSkillsStatus(done)
    } else {
      setSkillsMessage(
        result.kind === 'failed' ? result.message : SIGN_IN_AGAIN,
      )
    }
    setBusy(false)
  }
  const onAnalyse = () =>
    void runSkills(() => analyseSkills(auth, applicationId), null)
  const onSaveNotes = () =>
    void runSkills(
      () =>
        saveSkillNotes(
          auth,
          applicationId,
          Object.entries(notes)
            .map(([skill, text]) => ({ skill, text: text.trim() }))
            .filter((n) => n.text.length > 0),
        ),
      'Notes saved. They stay with this application.',
    )

  const onMatch = () => {
    if (!analysis) return
    setTicked((t) =>
      [...new Set([...t, ...analysis.include.map((s) => s.profileIndex)])].sort(
        (a, b) => a - b,
      ),
    )
    setTickedEvidenced((t) => [
      ...new Set([...t, ...(analysis.evidenced ?? []).map((s) => s.name)]),
    ])
  }
  const onClear = () => {
    setTicked([])
    setTickedEvidenced([])
  }

  const skillIndexes = analysis ? ticked : selection.skills

  const request = (basedOn: LetterDetail | null): GenerateRequest => {
    const asked = Object.entries(perParagraph)
      .map(([n, text]) => ({ n: Number(n), text: text.trim() }))
      .filter((c) => c.text.length > 0)
      .sort((a, b) => a.n - b.n)
    const generalText = general.trim()
    return {
      language: language === '' ? null : language,
      experience: selection.experience,
      projects: selection.projects,
      skills: skillIndexes,
      ...(analysis ? { evidencedSkills: tickedEvidenced } : {}),
      instructions: instructions.trim() === '' ? null : instructions.trim(),
      basedOnVersion: basedOn?.version ?? null,
      comments: basedOn
        ? {
            general: generalText === '' ? null : generalText,
            paragraphs: asked,
          }
        : null,
    }
  }

  const onGenerate = () =>
    void makeVersion(() => generateLetter(auth, applicationId, request(null)))
  const onRegenerate = () =>
    void makeVersion(() =>
      generateLetter(auth, applicationId, request(current)),
    )
  const onSaveEdit = () =>
    void makeVersion(() => saveEditedLetter(auth, applicationId, editing ?? ''))

  const onOpen = async (version: number) => {
    setBusy(true)
    setMessage(null)
    setStatus(null)
    const result = await readLetter(auth, applicationId, version)
    if (result.kind === 'done') show(result.letter)
    else setMessage(result.kind === 'failed' ? result.message : SIGN_IN_AGAIN)
    setBusy(false)
  }

  const onDelete = async (version: number) => {
    setBusy(true)
    setMessage(null)
    const result = await deleteLetter(auth, applicationId, version)
    if (result.kind === 'done') {
      const remaining = versions.filter((v) => v.version !== version)
      setVersions(remaining)
      if (current?.version === version) setCurrent(null)
    } else {
      setMessage(result.kind === 'failed' ? result.message : SIGN_IN_AGAIN)
    }
    setBusy(false)
  }

  const onCopy = async () => {
    if (!current) return
    try {
      await navigator.clipboard.writeText(current.text)
      setStatus('Copied to the clipboard.')
    } catch {
      setStatus(null)
      setMessage('The text could not be copied. Select it and copy by hand.')
    }
  }

  const onDownload = async () => {
    if (!current) return
    const result = await downloadLetter(auth, applicationId, current.version)
    if (result.kind === 'done') saveFile(result.blob, result.fileName)
    else setMessage(result.kind === 'failed' ? result.message : SIGN_IN_AGAIN)
  }

  const onDownloadPdf = async () => {
    if (!current) return
    const result = await downloadLetterPdf(auth, applicationId, current.version)
    if (result.kind === 'done') saveFile(result.blob, result.fileName)
    else setMessage(result.kind === 'failed' ? result.message : SIGN_IN_AGAIN)
  }

  const chosen =
    selection.experience.length +
    selection.projects.length +
    skillIndexes.length +
    (analysis ? tickedEvidenced.length : 0)
  const lines = current ? paragraphs(current.text) : []

  return (
    <Card
      title="Motivation letter"
      headingLevel={2}
      className="letter-workspace"
    >
      <Alert tone="warning">{CHECK_NOTICE}</Alert>

      <form aria-label="Letter settings" onSubmit={(e) => e.preventDefault()}>
        <FormField label="Language">
          {(control) => (
            <select
              {...control}
              value={language}
              onChange={(e) =>
                setLanguage(e.target.value as LetterLanguage | '')
              }
            >
              <option value="">Language of the posting</option>
              <option value="EN">English</option>
              <option value="DE">Deutsch</option>
            </select>
          )}
        </FormField>

        {profile.state === 'loading' && <p role="status">Loading profile…</p>}
        {profile.state === 'failed' && (
          <Alert tone="danger">{profile.message}</Alert>
        )}
        {profile.state === 'loaded' && (
          <>
            <Choice
              legend="Experience"
              max={MAX_EXPERIENCE}
              labels={profile.profile.experience.map(
                (e) => `${e.title} at ${e.employer}`,
              )}
              chosen={selection.experience}
              onChange={(experience) =>
                setSelection((s) => ({ ...s, experience }))
              }
            />
            <Choice
              legend="Open source projects"
              max={MAX_PROJECTS}
              labels={profile.profile.openSourceProjects.map((p) => p.name)}
              chosen={selection.projects}
              onChange={(projects) => setSelection((s) => ({ ...s, projects }))}
            />
            {!analysis && (
              <Choice
                legend="Skills"
                max={MAX_SKILLS}
                labels={profile.profile.skills.map((s) => s.name)}
                chosen={selection.skills}
                onChange={(skills) => setSelection((s) => ({ ...s, skills }))}
              />
            )}
          </>
        )}

        <SkillsStep
          analysis={analysis}
          loading={skillsLoading}
          busy={busy}
          message={skillsMessage}
          status={skillsStatus}
          ticked={ticked}
          tickedEvidenced={tickedEvidenced}
          notes={notes}
          onAnalyse={onAnalyse}
          onTick={(index, on) => setTicked((t) => toggled(t, index, on))}
          onTickEvidenced={(name, on) =>
            setTickedEvidenced((t) =>
              on
                ? [...t.filter((n) => n !== name), name]
                : t.filter((n) => n !== name),
            )
          }
          onMatch={onMatch}
          onClear={onClear}
          onNote={(skill, text) => setNotes((n) => ({ ...n, [skill]: text }))}
          onSaveNotes={onSaveNotes}
        />

        <FormField label="Instructions (optional)">
          {(control) => (
            <textarea
              {...control}
              rows={3}
              maxLength={1000}
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
            />
          )}
        </FormField>
        <div className="application-actions">
          <button
            type="button"
            disabled={busy || chosen === 0}
            onClick={onGenerate}
          >
            Generate
          </button>
        </div>
        {chosen === 0 && (
          <p>Choose at least one entry from your profile to generate.</p>
        )}
      </form>

      {busy && <p role="status">Working…</p>}
      {message && <Alert tone="danger">{message}</Alert>}
      {status && <p role="status">{status}</p>}
      {!loaded && <p role="status">Loading letters…</p>}

      {current && (
        <article aria-label={`Letter version ${current.version}`}>
          <h3>Version {current.version}</h3>
          {editing === null ? (
            <>
              <ol className="letter-paragraphs">
                {lines.map((text, i) => (
                  <li key={`${current.version}-${i}`}>
                    <p>{text}</p>
                    <FormField label={`Comment on paragraph ${i + 1}`}>
                      {(control) => (
                        <textarea
                          {...control}
                          rows={2}
                          maxLength={500}
                          value={perParagraph[i + 1] ?? ''}
                          onChange={(e) =>
                            setPerParagraph((c) => ({
                              ...c,
                              [i + 1]: e.target.value,
                            }))
                          }
                        />
                      )}
                    </FormField>
                  </li>
                ))}
              </ol>
              <FormField label="General comment">
                {(control) => (
                  <textarea
                    {...control}
                    rows={2}
                    maxLength={500}
                    value={general}
                    onChange={(e) => setGeneral(e.target.value)}
                  />
                )}
              </FormField>
              <div className="letter-actions">
                <button
                  type="button"
                  disabled={busy || chosen === 0}
                  onClick={onRegenerate}
                >
                  Regenerate
                </button>
                <button
                  type="button"
                  className="button--secondary"
                  disabled={busy}
                  onClick={() => setEditing(current.text)}
                >
                  Edit
                </button>
                <button
                  type="button"
                  className="button--secondary"
                  onClick={() => void onCopy()}
                >
                  Copy
                </button>
                <button
                  type="button"
                  className="button--secondary"
                  onClick={() => void onDownload()}
                >
                  Download .md
                </button>
                <button
                  type="button"
                  className="button--secondary"
                  onClick={() => void onDownloadPdf()}
                >
                  Download PDF
                </button>
              </div>
            </>
          ) : (
            <>
              <FormField label="Letter text">
                {(control) => (
                  <textarea
                    {...control}
                    rows={16}
                    value={editing}
                    onChange={(e) => setEditing(e.target.value)}
                  />
                )}
              </FormField>
              <div className="letter-actions">
                <button
                  type="button"
                  disabled={busy || editing.trim() === ''}
                  onClick={onSaveEdit}
                >
                  Save as new version
                </button>
                <button
                  type="button"
                  className="button--secondary"
                  onClick={() => setEditing(null)}
                >
                  Cancel
                </button>
              </div>
            </>
          )}
        </article>
      )}

      {versions.length > 0 && (
        <section
          aria-labelledby="letter-versions-heading"
          className="letter-versions"
        >
          <h3 id="letter-versions-heading">Versions</h3>
          <ul className="letter-versions__list">
            {[...versions]
              .sort((a, b) => b.version - a.version)
              .map((v) => (
                <li key={v.version} className="letter-versions__item">
                  <button
                    type="button"
                    className="button--secondary"
                    disabled={busy}
                    aria-current={current?.version === v.version}
                    onClick={() => void onOpen(v.version)}
                  >
                    Version {v.version}
                  </button>
                  <span className="letter-versions__text">
                    {HOW[v.how]}, {v.language}, {v.createdAt.slice(0, 10)}
                    {v.basedOnVersion !== null &&
                      `, from version ${v.basedOnVersion}`}
                  </span>
                  <button
                    type="button"
                    className="button--danger"
                    disabled={busy}
                    aria-label={`Remove version ${v.version}`}
                    onClick={() => void onDelete(v.version)}
                  >
                    Remove
                  </button>
                </li>
              ))}
          </ul>
        </section>
      )}
    </Card>
  )
}

const HOW = {
  GENERATED: 'generated',
  REGENERATED: 'regenerated',
  EDITED: 'edited',
} as const

function Choice({
  legend,
  labels,
  chosen,
  max,
  onChange,
}: {
  legend: string
  labels: string[]
  chosen: number[]
  max: number
  onChange: (next: number[]) => void
}) {
  return (
    <fieldset className="choice">
      <legend className="choice__legend">{legend}</legend>
      {labels.length === 0 && <p>Nothing saved in your profile yet.</p>}
      {labels.map((label, index) => {
        const on = chosen.includes(index)
        return (
          <label className="choice__option" key={index}>
            <input
              type="checkbox"
              checked={on}
              disabled={!on && chosen.length >= max}
              onChange={(e) =>
                onChange(toggled(chosen, index, e.target.checked))
              }
            />
            {label}
          </label>
        )
      })}
      {labels.length > 0 && <p className="choice__count">At most {max}.</p>}
    </fieldset>
  )
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
