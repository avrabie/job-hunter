import type { ReactNode } from 'react'

/** What a list shows when it has nothing yet: a short title, what to do, and an optional action. */
export function EmptyState({
  title,
  children,
  action,
}: {
  title: ReactNode
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <p className="empty-state__title">{title}</p>
      {children !== undefined && (
        <div className="empty-state__text">{children}</div>
      )}
      {action !== undefined && (
        <div className="empty-state__action">{action}</div>
      )}
    </div>
  )
}
