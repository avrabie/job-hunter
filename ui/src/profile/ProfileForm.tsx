import {
  LANGUAGE_LEVELS,
  REMOTE_MODES,
  SKILL_LEVELS,
  WORK_AUTHORISATIONS,
  blankEducation,
  blankExperience,
  blankLanguage,
  blankSkill,
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

interface Props {
  profile: Profile
  onChange: (profile: Profile) => void
  /** Server messages by field path, for example `experience[0].title`. */
  errors: Record<string, string>
}

/** All sections of the profile as editable fields. Holds no state of its own. */
export function ProfileForm({ profile, onChange, errors }: Props) {
  const set = <K extends keyof Profile>(key: K, value: Profile[K]) =>
    onChange({ ...profile, [key]: value })
  const location = profile.location ?? { city: null, canton: null }
  const prefs = profile.preferences
  const setPrefs = (patch: Partial<Profile['preferences']>) =>
    set('preferences', { ...prefs, ...patch })

  return (
    <>
      <section>
        <h2>About you</h2>
        <TextInput
          label="City"
          value={location.city}
          error={errors['location.city']}
          onChange={(city) => set('location', { ...location, city })}
        />
        <TextInput
          label="Canton"
          value={location.canton}
          error={errors['location.canton']}
          onChange={(canton) => set('location', { ...location, canton })}
        />
        <Select
          label="Work authorisation"
          value={profile.workAuthorisation}
          options={WORK_AUTHORISATIONS}
          empty="Not set"
          error={errors['workAuthorisation']}
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
                onChange={(code) => patch({ code })}
              />
              <Select
                label="Level"
                value={item.level}
                options={LANGUAGE_LEVELS}
                error={errors[`languages[${i}].level`]}
                onChange={(level) => level && patch({ level })}
              />
            </>
          )}
        </Repeater>
      </section>

      <section>
        <h2>Experience</h2>
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
                  onChange={(employer) => patch({ employer })}
                />
                <TextInput
                  label="Title"
                  value={item.title}
                  error={errors[`${at}.title`]}
                  onChange={(title) => patch({ title })}
                />
                <TextInput
                  label="Start"
                  type="month"
                  value={item.start}
                  error={errors[`${at}.start`]}
                  onChange={(start) => patch({ start })}
                />
                <TextInput
                  label="End (empty if current)"
                  type="month"
                  value={item.end}
                  error={errors[`${at}.end`]}
                  onChange={(end) => patch({ end: end === '' ? null : end })}
                />
                <TextInput
                  label="Location"
                  value={item.location}
                  error={errors[`${at}.location`]}
                  onChange={(location) => patch({ location })}
                />
                <StringList
                  title="Achievement"
                  items={item.achievements}
                  errors={errors}
                  path={`${at}.achievements`}
                  onChange={(achievements) => patch({ achievements })}
                />
              </>
            )
          }}
        </Repeater>
      </section>

      <section>
        <h2>Education</h2>
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
                  onChange={(institution) => patch({ institution })}
                />
                <TextInput
                  label="Degree"
                  value={item.degree}
                  error={errors[`${at}.degree`]}
                  onChange={(degree) => patch({ degree })}
                />
                <TextInput
                  label="Field of study"
                  value={item.field}
                  error={errors[`${at}.field`]}
                  onChange={(field) => patch({ field })}
                />
                <TextInput
                  label="Education start"
                  type="month"
                  value={item.start}
                  error={errors[`${at}.start`]}
                  onChange={(start) =>
                    patch({ start: start === '' ? null : start })
                  }
                />
                <TextInput
                  label="Education end"
                  type="month"
                  value={item.end}
                  error={errors[`${at}.end`]}
                  onChange={(end) => patch({ end: end === '' ? null : end })}
                />
              </>
            )
          }}
        </Repeater>
      </section>

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
                onChange={(name) => patch({ name })}
              />
              <Select
                label="Skill level"
                value={item.level}
                options={SKILL_LEVELS}
                empty="Not set"
                error={errors[`skills[${i}].level`]}
                onChange={(level) => patch({ level })}
              />
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
          onChange={(workloadMin) => setPrefs({ workloadMin })}
        />
        <NumberInput
          label="Workload to (%)"
          value={prefs.workloadMax}
          error={errors['preferences.workloadMax']}
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
          onChange={(minSalaryChf) => setPrefs({ minSalaryChf })}
        />
      </section>
    </>
  )
}

function toggle(modes: RemoteMode[], mode: RemoteMode, on: boolean) {
  const without = modes.filter((m) => m !== mode)
  return on ? [...without, mode] : without
}
