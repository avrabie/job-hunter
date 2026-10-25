import { useId, type ReactNode } from 'react'

/**
 * A bordered surface for one topic. With a `title` it is a `section` named by its heading, so it shows up in the
 * landmarks list; without one it is a plain box.
 */
export function Card({
  title,
  headingLevel = 2,
  actions,
  className,
  children,
}: {
  title?: ReactNode
  headingLevel?: 2 | 3 | 4
  /** Links or buttons at the bottom of the card. */
  actions?: ReactNode
  className?: string
  children?: ReactNode
}) {
  const headingId = useId()
  const Heading = `h${headingLevel}` as const
  const classes = className ? `card ${className}` : 'card'
  const body = (
    <>
      {title !== undefined && (
        <Heading id={headingId} className="card__title">
          {title}
        </Heading>
      )}
      {children}
      {actions !== undefined && <div className="card__actions">{actions}</div>}
    </>
  )
  return title !== undefined ? (
    <section className={classes} aria-labelledby={headingId}>
      {body}
    </section>
  ) : (
    <div className={classes}>{body}</div>
  )
}
