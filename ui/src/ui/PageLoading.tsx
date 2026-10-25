import type { ReactNode } from 'react'
import { PageHeader } from './PageHeader.tsx'

/** A page that is still loading: its heading is already there, so the layout does not jump when content arrives. */
export function PageLoading({
  title,
  text = 'Loading…',
}: {
  title: ReactNode
  text?: string
}) {
  return (
    <div className="page page--narrow">
      <PageHeader title={title} />
      <p role="status">{text}</p>
    </div>
  )
}
