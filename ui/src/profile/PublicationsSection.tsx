import type { ReactNode } from 'react'
import { NumberInput, Repeater, TextInput } from './fields.tsx'
import { blankPublication, type Publication } from './types.ts'

/** Papers, talks and the like. */
export function PublicationsSection({
  heading,
  publications,
  onChange,
  errors,
}: {
  /** The heading with its "Fill from CV" control; the form builds it. */
  heading: ReactNode
  publications: Publication[]
  onChange: (publications: Publication[]) => void
  errors: Record<string, string>
}) {
  return (
    <section aria-labelledby="publications-heading">
      {heading}
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
    </section>
  )
}
