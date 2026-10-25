import type { ReactNode } from 'react'
import { ProfileSection } from './ProfileSection.tsx'
import { LONG_LIST, countOf, hasErrorAt } from './sectionSummary.ts'
import { Repeater, TextInput } from './fields.tsx'
import { blankEducation, type Education } from './types.ts'

/** Schools and degrees. */
export function EducationSection({
  fill,
  education,
  onChange,
  errors,
}: {
  /** The "Fill from CV" control; the form builds it. */
  fill: ReactNode
  education: Education[]
  onChange: (education: Education[]) => void
  errors: Record<string, string>
}) {
  return (
    <ProfileSection
      name="education"
      title="Education"
      summary={countOf(education.length, 'entry', 'entries')}
      defaultOpen={education.length <= LONG_LIST}
      hasError={hasErrorAt(errors, ['education['])}
    >
      {fill}
      <Repeater
        title="Education entry"
        items={education}
        onChange={onChange}
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
    </ProfileSection>
  )
}
