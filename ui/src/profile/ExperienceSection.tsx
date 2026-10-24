import type { ReactNode } from 'react'
import { Repeater, StringList, TextInput } from './fields.tsx'
import { TagInput } from './TagInput.tsx'
import { blankExperience, type Experience } from './types.ts'

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

/** Positions held. */
export function ExperienceSection({
  heading,
  experience,
  onChange,
  errors,
  suggestions,
}: {
  /** The heading with its "Fill from CV" control; the form builds it. */
  heading: ReactNode
  experience: Experience[]
  onChange: (experience: Experience[]) => void
  errors: Record<string, string>
  /** Offered while a technologies field is typed in. */
  suggestions: string[]
}) {
  return (
    <section aria-labelledby="experience-heading">
      {heading}
      <Repeater
        title="Position"
        items={experience}
        onChange={onChange}
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
  )
}
