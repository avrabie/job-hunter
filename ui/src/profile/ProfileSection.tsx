import type { ReactNode } from 'react'
import { Collapsible } from '../ui/Collapsible.tsx'

/**
 * One section of the profile form: a region labelled by its `h2`, shown as a collapsible section with a one-line
 * summary. Closing it only hides the fields; they stay mounted, so their values and what is saved do not change.
 * A section with a message opens itself; the page's "focus the first invalid field" step opens it too.
 */
export function ProfileSection({
  name,
  title,
  summary,
  defaultOpen = true,
  hasError = false,
  sectionId,
  children,
}: {
  /** Short name used for the ids: the heading is `<name>-heading`, the details element `profile-<name>`. */
  name: string
  title: string
  summary: string
  defaultOpen?: boolean
  hasError?: boolean
  /** The id of the `section`, for links into the page such as `#contact`. */
  sectionId?: string
  children: ReactNode
}) {
  return (
    <section
      id={sectionId}
      className="profile-section"
      aria-labelledby={`${name}-heading`}
    >
      <Collapsible
        id={`profile-${name}`}
        title={title}
        titleAs="h2"
        titleId={`${name}-heading`}
        summary={summary}
        defaultOpen={defaultOpen}
        forceOpen={hasError}
      >
        {children}
      </Collapsible>
    </section>
  )
}
