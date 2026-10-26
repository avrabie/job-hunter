import { Alert } from '../ui/Alert.tsx'
import { Badge } from '../ui/Badge.tsx'
import { FormField } from '../ui/FormField.tsx'
import type { Importance, SkillAnalysis } from './skillsApi.ts'

export const NOTE_MAX = 500

export const SKILLS_EXPLANATION =
  'The job is read once and the skills it asks for are compared with your saved skills.'
export const NOTES_PRIVACY =
  'Notes stay with this application. Your profile is not changed.'

export type Analysed = Extract<SkillAnalysis, { analysed: true }>

const IMPORTANCE: Record<Importance, string> = {
  REQUIRED: 'Required',
  NICE_TO_HAVE: 'Nice to have',
}

function ImportanceBadge({ value }: { value: Importance }) {
  return (
    <Badge tone={value === 'REQUIRED' ? 'primary' : 'neutral'}>
      {IMPORTANCE[value]}
    </Badge>
  )
}

/** The optional "Skills for this job" step: analyse, tick skills, write notes for gaps. */
export function SkillsStep({
  analysis,
  loading,
  busy,
  message,
  status,
  ticked,
  tickedEvidenced,
  notes,
  onAnalyse,
  onTick,
  onTickEvidenced,
  onMatch,
  onClear,
  onNote,
  onSaveNotes,
}: {
  analysis: Analysed | null
  loading: boolean
  busy: boolean
  message: string | null
  status: string | null
  ticked: number[]
  tickedEvidenced: string[]
  notes: Record<string, string>
  onAnalyse: () => void
  onTick: (profileIndex: number, on: boolean) => void
  onTickEvidenced: (name: string, on: boolean) => void
  onMatch: () => void
  onClear: () => void
  onNote: (skill: string, text: string) => void
  onSaveNotes: () => void
}) {
  const tick = (name: string, profileIndex: number) => (
    <input
      type="checkbox"
      aria-label={name}
      checked={ticked.includes(profileIndex)}
      onChange={(e) => onTick(profileIndex, e.target.checked)}
    />
  )
  const evidenced = analysis?.evidenced ?? []
  return (
    <section aria-labelledby="skills-step-heading" className="skills-step">
      <h3 id="skills-step-heading">Skills for this job</h3>
      {loading && <p role="status">Loading skills…</p>}
      {!analysis && <p className="skills-step__hint">{SKILLS_EXPLANATION}</p>}
      <div className="application-actions">
        <button
          type="button"
          className={analysis ? 'button--secondary' : undefined}
          disabled={busy || loading}
          onClick={onAnalyse}
        >
          {analysis ? 'Analyse the job again' : 'Analyse the job'}
        </button>
      </div>
      {message && <Alert tone="danger">{message}</Alert>}
      {status && <p role="status">{status}</p>}

      {analysis && (
        <>
          <div className="application-actions">
            <button
              type="button"
              className="button--secondary"
              onClick={onMatch}
            >
              Match
            </button>
            <button
              type="button"
              className="button--secondary"
              onClick={onClear}
            >
              Clear
            </button>
          </div>
          <fieldset className="choice">
            <legend className="choice__legend">Advised to include</legend>
            {analysis.include.length === 0 && (
              <p>None of the asked-for skills is in your profile.</p>
            )}
            {analysis.include.map((s) => (
              <label className="choice__option" key={s.profileIndex}>
                {tick(s.profileName ?? s.name, s.profileIndex)}
                {s.profileName ?? s.name}{' '}
                <ImportanceBadge value={s.importance} />
              </label>
            ))}
          </fieldset>

          <fieldset className="choice">
            <legend className="choice__legend">
              Evidenced in your CV and projects
            </legend>
            {evidenced.length === 0 && <p>No skills evidenced.</p>}
            {evidenced.map((s) => (
              <div className="skills-step__evidence" key={s.name}>
                <label className="choice__option">
                  <input
                    type="checkbox"
                    aria-label={s.name}
                    checked={tickedEvidenced.includes(s.name)}
                    onChange={(e) => onTickEvidenced(s.name, e.target.checked)}
                  />
                  {s.name} <ImportanceBadge value={s.importance} />
                </label>
                <p className="choice__count">{s.source}</p>
                <blockquote className="skills-step__quote">
                  {s.quote}
                </blockquote>
              </div>
            ))}
          </fieldset>

          <fieldset className="choice">
            <legend className="choice__legend">Gaps</legend>
            {analysis.gaps.length === 0 && <p>No gaps found.</p>}
            {analysis.gaps.map((g) => (
              <div className="skills-step__gap" key={g.name}>
                <p className="skills-step__gap-title">
                  {g.name} <ImportanceBadge value={g.importance} />
                </p>
                {g.related.length > 0 && (
                  <p className="choice__count">
                    Related skills of yours:{' '}
                    {g.related.map((r) => r.name).join(', ')}
                  </p>
                )}
                <FormField label={`Note on ${g.name}`}>
                  {(control) => (
                    <textarea
                      {...control}
                      rows={2}
                      maxLength={NOTE_MAX}
                      value={notes[g.name] ?? ''}
                      onChange={(e) => onNote(g.name, e.target.value)}
                    />
                  )}
                </FormField>
                <p className="choice__count">
                  {(notes[g.name] ?? '').length} / {NOTE_MAX}
                </p>
              </div>
            ))}
            {analysis.gaps.length > 0 && (
              <>
                <p className="choice__count">{NOTES_PRIVACY}</p>
                <div className="application-actions">
                  <button
                    type="button"
                    className="button--secondary"
                    disabled={busy}
                    onClick={onSaveNotes}
                  >
                    Save notes
                  </button>
                </div>
              </>
            )}
          </fieldset>

          <fieldset className="choice">
            <legend className="choice__legend">Your other skills</legend>
            {analysis.other.length === 0 && <p>No other skills.</p>}
            {analysis.other.map((s) => (
              <label className="choice__option" key={s.profileIndex}>
                {tick(s.name, s.profileIndex)}
                {s.name}
              </label>
            ))}
          </fieldset>
        </>
      )}
    </section>
  )
}
