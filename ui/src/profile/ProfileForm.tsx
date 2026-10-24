import { Link } from 'react-router'
import type { ProfileSectionName } from '../cv/cvApi.ts'
import {
  LANGUAGE_LEVELS,
  REMOTE_MODES,
  SKILL_LEVELS,
  WORK_AUTHORISATIONS,
  blankEducation,
  blankExperience,
  blankLanguage,
  blankPublication,
  blankSkill,
  type CodeProvider,
  type OpenSourceProject,
  type Profile,
  type RemoteMode,
} from './types.ts'
import {
  Field,
  NumberInput,
  Repeater,
  Select,
  StringList,
  TextInput,
} from './fields.tsx'
import type { AnalysisResult } from './analysisApi.ts'
import { OpenSourceSection } from './OpenSourceSection.tsx'
import type { RepositoriesResult } from './repositoriesApi.ts'
import { skillsAfterProjectChange, sourcesOf } from './skills.ts'
import { TagInput } from './TagInput.tsx'

/** The first server message for a list field or for one of its entries, such as `experience[0].technologies[1]`. */
function listError(
  errors: Record<string, string>,
  path: string,
): string | undefined {
  const key = Object.keys(errors).find(
    (k) => k === path || k.startsWith(`${path}[`),
  )
  return key === undefined ? undefined : errors[key]
}

interface Props {
  profile: Profile
  onChange: (profile: Profile) => void
  /** Server messages by field path, for example `experience[0].title`. */
  errors: Record<string, string>
  /** Called with the path of a field the user has just left. */
  onLeave?: (path: string) => void
  /** Whether the user has a master CV; without one the section buttons are disabled. */
  canFillFromCv: boolean
  /** Asks for one section to be read from the CV. */
  onFillSection: (section: ProfileSectionName) => void
  /** Per section: whether its request is running and the last fixed message. */
  fillState: Record<ProfileSectionName, FillState>
  /** Lists the public repositories of one code account. */
  onLoadRepositories: (
    provider: CodeProvider,
    username: string,
  ) => Promise<RepositoriesResult>
  /** The projects in the last loaded or saved profile; only these can be analysed. */
  savedProjects: OpenSourceProject[]
  /** Analyses one saved project; the page applies a success to the project and the skills. */
  onAnalyze: (project: OpenSourceProject) => Promise<AnalysisResult>
}

export interface FillState {
  loading: boolean
  error: string | null
}

/** All sections of the profile as editable fields. Holds no state of its own. */
export function ProfileForm({
  profile,
  onChange,
  errors,
  onLeave,
  canFillFromCv,
  onFillSection,
  fillState,
  onLoadRepositories,
  savedProjects,
  onAnalyze,
}: Props) {
  const set = <K extends keyof Profile>(key: K, value: Profile[K]) =>
    onChange({ ...profile, [key]: value })
  const contact = profile.contact
  const location = profile.location ?? { city: null, canton: null }
  const prefs = profile.preferences
  const setPrefs = (patch: Partial<Profile['preferences']>) =>
    set('preferences', { ...prefs, ...patch })
  // Offered while a technologies field is typed in: the skills and the tags already used in positions.
  const suggestions = [
    ...profile.skills.map((s) => s.name),
    ...profile.experience.flatMap((e) => e.technologies),
  ]

  /** A section heading with its "Fill from CV" button, status and hint. */
  const heading = (section: ProfileSectionName, title: string) => {
    const state = fillState[section]
    return (
      <div className="section-heading">
        <h2 id={`${section}-heading`}>{title}</h2>
        <button
          type="button"
          className="secondary"
          disabled={!canFillFromCv || state.loading}
          onClick={() => onFillSection(section)}
        >
          Fill from CV
        </button>
        {!canFillFromCv && (
          <p className="hint">Choose a master CV in your files to use this.</p>
        )}
        {state.loading && <p role="status">Reading your CV…</p>}
        {state.error && <p role="alert">{state.error}</p>}
      </div>
    )
  }

  return (
    <div
      onBlur={(e) => {
        const path = (e.target as HTMLElement).dataset['path']
        if (path) onLeave?.(path)
      }}
    >
      <section aria-labelledby="contact-heading">
        <h2 id="contact-heading">Contact</h2>
        <p className="hint">
          The full name is needed to export a CV.{' '}
          <Link to="/export">Export CV</Link>
        </p>
        <TextInput
          label="Full name"
          value={contact.fullName}
          error={errors['contact.fullName']}
          path={'contact.fullName'}
          onChange={(fullName) => set('contact', { ...contact, fullName })}
        />
        <TextInput
          label="Headline"
          value={contact.headline}
          error={errors['contact.headline']}
          path={'contact.headline'}
          onChange={(headline) => set('contact', { ...contact, headline })}
        />
        <TextInput
          label="Email"
          value={contact.email}
          error={errors['contact.email']}
          path={'contact.email'}
          onChange={(email) => set('contact', { ...contact, email })}
        />
        <TextInput
          label="Phone"
          value={contact.phone}
          error={errors['contact.phone']}
          path={'contact.phone'}
          onChange={(phone) => set('contact', { ...contact, phone })}
        />
        <TextInput
          label="Website"
          value={contact.website}
          error={errors['contact.website']}
          path={'contact.website'}
          onChange={(website) => set('contact', { ...contact, website })}
        />
        <TextInput
          label="LinkedIn"
          value={contact.linkedin}
          error={errors['contact.linkedin']}
          path={'contact.linkedin'}
          onChange={(linkedin) => set('contact', { ...contact, linkedin })}
        />
      </section>

      <section>
        <h2>About you</h2>
        <TextInput
          label="City"
          value={location.city}
          error={errors['location.city']}
          path={'location.city'}
          onChange={(city) => set('location', { ...location, city })}
        />
        <TextInput
          label="Canton"
          value={location.canton}
          error={errors['location.canton']}
          path={'location.canton'}
          onChange={(canton) => set('location', { ...location, canton })}
        />
        <Select
          label="Work authorisation"
          value={profile.workAuthorisation}
          options={WORK_AUTHORISATIONS}
          empty="Not set"
          error={errors['workAuthorisation']}
          path={'workAuthorisation'}
          onChange={(v) => set('workAuthorisation', v)}
        />
        <Field label="Summary" error={errors['summary']}>
          <textarea
            rows={4}
            value={profile.summary ?? ''}
            onChange={(e) => set('summary', e.target.value)}
          />
        </Field>
      </section>

      <section>
        <h2>Languages</h2>
        <Repeater
          title="Language"
          items={profile.languages}
          onChange={(v) => set('languages', v)}
          blank={blankLanguage}
        >
          {(item, patch, i) => (
            <>
              <TextInput
                label="Language code"
                value={item.code}
                error={errors[`languages[${i}].code`]}
                path={`languages[${i}].code`}
                onChange={(code) => patch({ code })}
              />
              <Select
                label="Level"
                value={item.level}
                options={LANGUAGE_LEVELS}
                error={errors[`languages[${i}].level`]}
                path={`languages[${i}].level`}
                onChange={(level) => level && patch({ level })}
              />
            </>
          )}
        </Repeater>
      </section>

      <section aria-labelledby="experience-heading">
        {heading('experience', 'Experience')}
        <Repeater
          title="Position"
          items={profile.experience}
          onChange={(v) => set('experience', v)}
          blank={blankExperience}
        >
          {(item, patch, i) => {
            const at = `experience[${i}]`
            return (
              <>
                <TextInput
                  label="Employer"
                  value={item.employer}
                  error={errors[`${at}.employer`]}
                  path={`${at}.employer`}
                  onChange={(employer) => patch({ employer })}
                />
                <TextInput
                  label="Title"
                  value={item.title}
                  error={errors[`${at}.title`]}
                  path={`${at}.title`}
                  onChange={(title) => patch({ title })}
                />
                <TextInput
                  label="Start"
                  type="month"
                  value={item.start}
                  error={errors[`${at}.start`]}
                  path={`${at}.start`}
                  onChange={(start) => patch({ start })}
                />
                <TextInput
                  label="End (empty if current)"
                  type="month"
                  value={item.end}
                  error={errors[`${at}.end`]}
                  path={`${at}.end`}
                  onChange={(end) => patch({ end: end === '' ? null : end })}
                />
                <TextInput
                  label="Location"
                  value={item.location}
                  error={errors[`${at}.location`]}
                  path={`${at}.location`}
                  onChange={(location) => patch({ location })}
                />
                <StringList
                  title="Achievement"
                  items={item.achievements}
                  errors={errors}
                  path={`${at}.achievements`}
                  onChange={(achievements) => patch({ achievements })}
                />
                <TagInput
                  label="Technologies"
                  value={item.technologies}
                  suggestions={suggestions}
                  error={listError(errors, `${at}.technologies`)}
                  path={`${at}.technologies`}
                  onChange={(technologies) => patch({ technologies })}
                />
              </>
            )
          }}
        </Repeater>
      </section>

      <section aria-labelledby="education-heading">
        {heading('education', 'Education')}
        <Repeater
          title="Education entry"
          items={profile.education}
          onChange={(v) => set('education', v)}
          blank={blankEducation}
        >
          {(item, patch, i) => {
            const at = `education[${i}]`
            return (
              <>
                <TextInput
                  label="Institution"
                  value={item.institution}
                  error={errors[`${at}.institution`]}
                  path={`${at}.institution`}
                  onChange={(institution) => patch({ institution })}
                />
                <TextInput
                  label="Degree"
                  value={item.degree}
                  error={errors[`${at}.degree`]}
                  path={`${at}.degree`}
                  onChange={(degree) => patch({ degree })}
                />
                <TextInput
                  label="Field of study"
                  value={item.field}
                  error={errors[`${at}.field`]}
                  path={`${at}.field`}
                  onChange={(field) => patch({ field })}
                />
                <TextInput
                  label="Education start"
                  type="month"
                  value={item.start}
                  error={errors[`${at}.start`]}
                  path={`${at}.start`}
                  onChange={(start) =>
                    patch({ start: start === '' ? null : start })
                  }
                />
                <TextInput
                  label="Education end"
                  type="month"
                  value={item.end}
                  error={errors[`${at}.end`]}
                  path={`${at}.end`}
                  onChange={(end) => patch({ end: end === '' ? null : end })}
                />
              </>
            )
          }}
        </Repeater>
      </section>

      <section aria-labelledby="publications-heading">
        {heading('publications', 'Publications')}
        <Repeater
          title="Publication"
          items={profile.publications}
          onChange={(v) => set('publications', v)}
          blank={blankPublication}
        >
          {(item, patch, i) => {
            const at = `publications[${i}]`
            return (
              <>
                <TextInput
                  label="Title"
                  value={item.title}
                  error={errors[`${at}.title`]}
                  path={`${at}.title`}
                  onChange={(title) => patch({ title })}
                />
                <TextInput
                  label="Venue"
                  value={item.venue}
                  error={errors[`${at}.venue`]}
                  path={`${at}.venue`}
                  onChange={(venue) => patch({ venue })}
                />
                <NumberInput
                  label="Year"
                  value={item.year}
                  error={errors[`${at}.year`]}
                  path={`${at}.year`}
                  onChange={(year) => patch({ year })}
                />
                <TextInput
                  label="Authors"
                  value={item.authors}
                  error={errors[`${at}.authors`]}
                  path={`${at}.authors`}
                  onChange={(authors) => patch({ authors })}
                />
                <TextInput
                  label="Link"
                  value={item.url}
                  error={errors[`${at}.url`]}
                  path={`${at}.url`}
                  onChange={(url) => patch({ url })}
                />
              </>
            )
          }}
        </Repeater>
      </section>

      <OpenSourceSection
        accounts={profile.codeAccounts}
        onAccountsChange={(v) => set('codeAccounts', v)}
        projects={profile.openSourceProjects}
        onProjectsChange={(v) =>
          onChange({
            ...profile,
            openSourceProjects: v,
            skills: skillsAfterProjectChange(
              profile.skills,
              profile.openSourceProjects,
              v,
            ),
          })
        }
        saved={savedProjects}
        onAnalyze={onAnalyze}
        errors={errors}
        onLoad={onLoadRepositories}
      />

      <section>
        <h2>Skills</h2>
        <Repeater
          title="Skill"
          items={profile.skills}
          onChange={(v) => set('skills', v)}
          blank={blankSkill}
        >
          {(item, patch, i) => (
            <>
              <TextInput
                label="Skill name"
                value={item.name}
                error={errors[`skills[${i}].name`]}
                path={`skills[${i}].name`}
                onChange={(name) => patch({ name })}
              />
              <Select
                label="Skill level"
                value={item.level}
                options={SKILL_LEVELS}
                empty="Not set"
                error={errors[`skills[${i}].level`]}
                path={`skills[${i}].level`}
                onChange={(level) => patch({ level })}
              />
              {item.origin === 'ANALYSIS' && (
                <p className="hint">from repository analysis</p>
              )}
              {sourcesOf(item).length > 0 && (
                <ul aria-label={`Sources of Skill ${i + 1}`}>
                  {sourcesOf(item).map((s, j) => (
                    <li key={`${s.provider}:${s.fullName}`}>
                      <a href={s.url} target="_blank" rel="noreferrer">
                        {s.fullName}
                      </a>{' '}
                      <span className="hint">{s.evidence}</span>{' '}
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Remove source ${j + 1} of Skill ${i + 1}`}
                        onClick={() =>
                          patch({
                            sources: sourcesOf(item).filter((_, k) => k !== j),
                          })
                        }
                      >
                        Remove source
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </Repeater>
      </section>

      <section>
        <h2>Preferences</h2>
        <StringList
          title="Target role"
          items={prefs.targetRoles}
          errors={errors}
          path="preferences.targetRoles"
          onChange={(targetRoles) => setPrefs({ targetRoles })}
        />
        <NumberInput
          label="Workload from (%)"
          value={prefs.workloadMin}
          error={errors['preferences.workloadMin']}
          path={'preferences.workloadMin'}
          onChange={(workloadMin) => setPrefs({ workloadMin })}
        />
        <NumberInput
          label="Workload to (%)"
          value={prefs.workloadMax}
          error={errors['preferences.workloadMax']}
          path={'preferences.workloadMax'}
          onChange={(workloadMax) => setPrefs({ workloadMax })}
        />
        <fieldset>
          <legend>Work location</legend>
          {REMOTE_MODES.map((mode) => (
            <label key={mode} className="inline">
              <input
                type="checkbox"
                checked={prefs.remoteModes.includes(mode)}
                onChange={(e) =>
                  setPrefs({
                    remoteModes: toggle(
                      prefs.remoteModes,
                      mode,
                      e.target.checked,
                    ),
                  })
                }
              />
              {mode}
            </label>
          ))}
        </fieldset>
        <StringList
          title="Preferred canton"
          items={prefs.cantons}
          errors={errors}
          path="preferences.cantons"
          onChange={(cantons) => setPrefs({ cantons })}
        />
        <NumberInput
          label="Minimum yearly salary (CHF)"
          value={prefs.minSalaryChf}
          error={errors['preferences.minSalaryChf']}
          path={'preferences.minSalaryChf'}
          onChange={(minSalaryChf) => setPrefs({ minSalaryChf })}
        />
      </section>
    </div>
  )
}

function toggle(modes: RemoteMode[], mode: RemoteMode, on: boolean) {
  const without = modes.filter((m) => m !== mode)
  return on ? [...without, mode] : without
}
