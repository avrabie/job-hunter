import type { ReactNode } from 'react'
import { ProfileSection } from './ProfileSection.tsx'
import { LONG_LIST, countOf, hasErrorAt } from './sectionSummary.ts'
import { NumberInput, Repeater, TextInput } from './fields.tsx'
import { blankPublication, type Publication } from './types.ts'

/** Papers, talks and the like. */
export function PublicationsSection({
  fill,
  publications,
  onChange,
  errors,
}: {
  /** The "Fill from CV" control; the form builds it. */
  fill: ReactNode
  publications: Publication[]
  onChange: (publications: Publication[]) => void
  errors: Record<string, string>
}) {
  return (
    <ProfileSection
      name="publications"
      title="Publications"
      summary={countOf(publications.length, 'publication', 'publications')}
      defaultOpen={publications.length <= LONG_LIST}
      hasError={hasErrorAt(errors, ['publications['])}
    >
      {fill}
      <Repeater
        title="Publication"
        items={publications}
        onChange={onChange}
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
    </ProfileSection>
  )
}
