import { Field, Select, TextInput } from './fields.tsx'
import { ProfileSection } from './ProfileSection.tsx'
import { hasErrorAt } from './sectionSummary.ts'
import { WORK_AUTHORISATIONS, type Location, type Profile } from './types.ts'

/** Where you live, work authorisation and the summary. */
export function AboutSection({
  location,
  workAuthorisation,
  summary,
  onLocationChange,
  onWorkAuthorisationChange,
  onSummaryChange,
  errors,
}: {
  location: Location
  workAuthorisation: Profile['workAuthorisation']
  summary: string | null
  onLocationChange: (location: Location) => void
  onWorkAuthorisationChange: (value: Profile['workAuthorisation']) => void
  onSummaryChange: (summary: string) => void
  errors: Record<string, string>
}) {
  const place = [location.city, location.canton]
    .map((v) => v?.trim())
    .filter(Boolean)
    .join(', ')
  return (
    <ProfileSection
      name="about"
      title="About you"
      summary={place || 'Not set'}
      hasError={hasErrorAt(errors, [
        'location.',
        'workAuthorisation',
        'summary',
      ])}
    >
      <TextInput
        label="City"
        value={location.city}
        error={errors['location.city']}
        path={'location.city'}
        onChange={(city) => onLocationChange({ ...location, city })}
      />
      <TextInput
        label="Canton"
        value={location.canton}
        error={errors['location.canton']}
        path={'location.canton'}
        onChange={(canton) => onLocationChange({ ...location, canton })}
      />
      <Select
        label="Work authorisation"
        value={workAuthorisation}
        options={WORK_AUTHORISATIONS}
        empty="Not set"
        error={errors['workAuthorisation']}
        path={'workAuthorisation'}
        onChange={(v) => onWorkAuthorisationChange(v)}
      />
      <Field label="Summary" error={errors['summary']}>
        {(control) => (
          <textarea
            {...control}
            rows={4}
            value={summary ?? ''}
            onChange={(e) => onSummaryChange(e.target.value)}
          />
        )}
      </Field>
    </ProfileSection>
  )
}
