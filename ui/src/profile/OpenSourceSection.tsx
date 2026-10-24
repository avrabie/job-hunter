import { useState } from 'react'
import type { AnalysisResult } from './analysisApi.ts'
import { Repeater, Select, TextInput } from './fields.tsx'
import type { RepositoriesResult } from './repositoriesApi.ts'
import { isValidUsername } from './rules.ts'
import {
  CODE_PROVIDERS,
  sameRepository,
  type CodeAccount,
  type CodeProvider,
  type OpenSourceProject,
  type Repository,
} from './types.ts'

const PROVIDER_LABELS: Record<CodeProvider, string> = { GITHUB: 'GitHub' }

const blankAccount = (): CodeAccount => ({ provider: 'GITHUB', username: '' })

/** A picked repository copies the public facts of the listing; the note and the analysis start empty. */
const pick = (repo: Repository): OpenSourceProject => ({
  provider: repo.provider,
  fullName: repo.fullName,
  name: repo.name,
  url: repo.url,
  description: repo.description,
  language: repo.language,
  topics: repo.topics,
  stars: repo.stars,
  note: null,
  analysis: null,
})

type Listing =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'listed'; repositories: Repository[] }
  | { state: 'failed'; message: string }

interface Props {
  accounts: CodeAccount[]
  onAccountsChange: (accounts: CodeAccount[]) => void
  projects: OpenSourceProject[]
  onProjectsChange: (projects: OpenSourceProject[]) => void
  /** The projects in the last loaded or saved profile; only these can be analysed. */
  saved: OpenSourceProject[]
  /** Analyses one saved project; the page puts a success on the project and in the skills. */
  onAnalyze: (project: OpenSourceProject) => Promise<AnalysisResult>
  /** Server messages by field path, for example `openSourceProjects[0].note`. */
  errors: Record<string, string>
  /** Lists the repositories of one account; nothing is saved by it. */
  onLoad: (
    provider: CodeProvider,
    username: string,
  ) => Promise<RepositoriesResult>
}

/**
 * The Open Source Projects section: the code accounts the user links, the repositories of an account the user picks
 * from a checklist, and the picked list with a note and an analysis on each. Only the picked list is saved.
 */
export function OpenSourceSection({
  accounts,
  onAccountsChange,
  projects,
  onProjectsChange,
  saved,
  onAnalyze,
  errors,
  onLoad,
}: Props) {
  const [listing, setListing] = useState<Listing>({ state: 'idle' })
  // Set once the user ticks or unticks a repository in this visit; the page then says nothing is saved yet.
  const [picking, setPicking] = useState(false)
  // The project being analysed (by its position in the list) and the fixed failure message per project.
  const [analysing, setAnalysing] = useState<number | null>(null)
  const [failures, setFailures] = useState<Record<number, string>>({})

  async function load(provider: CodeProvider, username: string) {
    setListing({ state: 'loading' })
    const result = await onLoad(provider, username.trim())
    if (result.kind === 'found')
      setListing({ state: 'listed', repositories: result.repositories })
    else if (result.kind === 'failed')
      setListing({ state: 'failed', message: result.message })
    else setListing({ state: 'idle' })
  }

  async function analyse(i: number) {
    const project = projects[i]!
    if (
      project.analysis &&
      !window.confirm(
        `Replace the analysis of ${project.name}? Its skills from the earlier analysis are replaced too.`,
      )
    )
      return
    setAnalysing(i)
    setFailures((current) => {
      const next = { ...current }
      delete next[i]
      return next
    })
    const result = await onAnalyze(project)
    setAnalysing(null)
    if (result.kind === 'failed') {
      const message = result.message
      setFailures((current) => ({ ...current, [i]: message }))
    }
  }

  function togglePick(repo: Repository, on: boolean) {
    setPicking(true)
    if (on) onProjectsChange([...projects, pick(repo)])
    else onProjectsChange(projects.filter((p) => !sameRepository(p, repo)))
  }

  function setProject(i: number, patch: Partial<OpenSourceProject>) {
    onProjectsChange(projects.map((p, j) => (j === i ? { ...p, ...patch } : p)))
  }

  function moveProject(i: number, to: number) {
    if (to < 0 || to >= projects.length) return
    const next = [...projects]
    const [item] = next.splice(i, 1)
    next.splice(to, 0, item!)
    onProjectsChange(next)
  }

  return (
    <section aria-labelledby="open-source-heading">
      <h2 id="open-source-heading">Open Source Projects</h2>
      <h3>Code accounts</h3>
      <Repeater
        title="Code account"
        items={accounts}
        onChange={onAccountsChange}
        blank={blankAccount}
        max={CODE_PROVIDERS.length}
      >
        {(item, patch, i) => {
          const at = `codeAccounts[${i}]`
          const usable = isValidUsername(item.provider, item.username.trim())
          return (
            <>
              <Select
                label="Provider"
                value={item.provider}
                options={CODE_PROVIDERS}
                labels={PROVIDER_LABELS}
                error={errors[`${at}.provider`]}
                path={`${at}.provider`}
                onChange={(provider) => provider && patch({ provider })}
              />
              <TextInput
                label="Username"
                value={item.username}
                error={errors[`${at}.username`]}
                path={`${at}.username`}
                onChange={(username) => patch({ username })}
              />
              <button
                type="button"
                className="secondary"
                disabled={!usable || listing.state === 'loading'}
                onClick={() => void load(item.provider, item.username)}
              >
                Load repositories
              </button>
            </>
          )
        }}
      </Repeater>

      {listing.state === 'loading' && (
        <p role="status">Loading repositories…</p>
      )}
      {listing.state === 'failed' && <p role="alert">{listing.message}</p>}
      {listing.state === 'listed' && (
        <fieldset>
          <legend>Repositories</legend>
          {listing.repositories.length === 0 && (
            <p className="hint">This account has no public repositories.</p>
          )}
          {listing.repositories.map((repo) => (
            <label key={`${repo.provider}:${repo.fullName}`} className="inline">
              <input
                type="checkbox"
                checked={projects.some((p) => sameRepository(p, repo))}
                onChange={(e) => togglePick(repo, e.target.checked)}
              />
              <span>{repo.name}</span>
              {repo.description && (
                <span className="hint">{repo.description}</span>
              )}
              <span className="hint">
                {[
                  repo.language,
                  repo.stars === null ? null : `${repo.stars} stars`,
                ]
                  .filter(Boolean)
                  .join(', ')}
              </span>
            </label>
          ))}
        </fieldset>
      )}

      {picking && (
        <p className="notice" role="status">
          Nothing is saved until you press Save.
        </p>
      )}
      <h3>Picked projects</h3>
      {projects.length === 0 && (
        <p className="hint">No repositories picked yet.</p>
      )}
      {projects.map((p, i) => {
        const at = `openSourceProjects[${i}]`
        const isSaved = saved.some((s) => sameRepository(s, p))
        const busy = analysing !== null
        return (
          <fieldset key={`${p.provider}:${p.fullName}`}>
            <legend>{`Project ${i + 1}`}</legend>
            <p>
              <a href={p.url} target="_blank" rel="noreferrer">
                {p.name}
              </a>
            </p>
            {p.description && <p>{p.description}</p>}
            <p className="hint">
              {[
                p.language,
                p.stars === null ? null : `${p.stars} stars`,
                p.topics.join(', ') || null,
              ]
                .filter(Boolean)
                .join(' · ')}
            </p>
            <TextInput
              label="Note"
              value={p.note}
              error={errors[`${at}.note`]}
              path={`${at}.note`}
              onChange={(note) =>
                setProject(i, { note: note === '' ? null : note })
              }
            />

            <div className="analysis">
              {!isSaved && (
                <p className="hint">Save this project first to analyze it.</p>
              )}
              <div className="actions compact">
                <button
                  type="button"
                  className="secondary"
                  aria-label={`Analyze Project ${i + 1}`}
                  disabled={!isSaved || busy}
                  onClick={() => void analyse(i)}
                >
                  {analysing === i ? 'Analyzing…' : 'Analyze'}
                </button>
                {p.analysis && (
                  <button
                    type="button"
                    className="secondary"
                    aria-label={`Remove analysis of Project ${i + 1}`}
                    disabled={busy}
                    onClick={() => setProject(i, { analysis: null })}
                  >
                    Remove analysis
                  </button>
                )}
              </div>
              {analysing === i && (
                <p role="status">
                  Reading the repository. This can take a minute.
                </p>
              )}
              {failures[i] && <p role="alert">{failures[i]}</p>}
              {p.analysis && (
                <div>
                  <h4>Analysis</h4>
                  <p>{p.analysis.summary}</p>
                  <p>
                    <span className="hint">Architecture: </span>
                    {p.analysis.architecture}
                  </p>
                  <h5>Tech stack</h5>
                  <ul>
                    {p.analysis.techStack.map((t) => (
                      <li key={t.name}>
                        <strong>{t.name}</strong>{' '}
                        <span className="hint">{t.category}</span>
                        {` · ${t.evidencePath}: ${t.evidence}`}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <div className="actions compact">
              <button
                type="button"
                className="secondary"
                aria-label={`Move Project ${i + 1} up`}
                disabled={i === 0}
                onClick={() => moveProject(i, i - 1)}
              >
                Up
              </button>
              <button
                type="button"
                className="secondary"
                aria-label={`Move Project ${i + 1} down`}
                disabled={i === projects.length - 1}
                onClick={() => moveProject(i, i + 1)}
              >
                Down
              </button>
              <button
                type="button"
                className="secondary"
                aria-label={`Remove Project ${i + 1}`}
                onClick={() =>
                  onProjectsChange(projects.filter((_, j) => j !== i))
                }
              >
                Remove
              </button>
            </div>
          </fieldset>
        )
      })}
    </section>
  )
}
