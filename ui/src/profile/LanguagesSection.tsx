import { Repeater, Select, TextInput } from './fields.tsx'
import { ProfileSection } from './ProfileSection.tsx'
import { LONG_LIST, countOf, hasErrorAt } from './sectionSummary.ts'
import { LANGUAGE_LEVELS, blankLanguage, type LanguageSkill } from './types.ts'

/** Spoken languages with their level. */
export function LanguagesSection({
  languages,
  onChange,
  errors,
}: {
  languages: LanguageSkill[]
  onChange: (languages: LanguageSkill[]) => void
  errors: Record<string, string>
}) {
  return (
    <ProfileSection
      name="languages"
      title="Languages"
      summary={countOf(languages.length, 'language', 'languages')}
      defaultOpen={languages.length <= LONG_LIST}
      hasError={hasErrorAt(errors, ['languages['])}
    >
      <Repeater
        title="Language"
        items={languages}
        onChange={onChange}
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
    </ProfileSection>
  )
}
