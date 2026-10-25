import type { ReactNode } from 'react'

export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

/** A short label such as a stage, a count or a status. The tone is decoration: the text must say it all. */
export function Badge({
  tone = 'neutral',
  children,
}: {
  tone?: Tone
  children: ReactNode
}) {
  return <span className={`badge badge--${tone}`}>{children}</span>
}
