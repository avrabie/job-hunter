import { NumberInput, StringList } from './fields.tsx'
import { REMOTE_MODES, type Preferences, type RemoteMode } from './types.ts'

function toggle(modes: RemoteMode[], mode: RemoteMode, on: boolean) {
  const without = modes.filter((m) => m !== mode)
  return on ? [...without, mode] : without
}

/** What the next job should look like. */
export function PreferencesSection({
  prefs,
  onChange,
  errors,
}: {
  prefs: Preferences
  onChange: (prefs: Preferences) => void
  errors: Record<string, string>
}) {
  const setPrefs = (patch: Partial<Preferences>) =>
    onChange({ ...prefs, ...patch })

  return (
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
  )
}
