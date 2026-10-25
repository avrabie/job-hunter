import type { ReactNode } from 'react'

/** The top of a page: its one `h1`, an optional lead sentence and actions that apply to the whole page. */
export function PageHeader({
  title,
  lead,
  actions,
}: {
  title: ReactNode
  lead?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="page-header">
      <div className="page-header__text">
        <h1>{title}</h1>
        {lead !== undefined && <p className="page-header__lead">{lead}</p>}
      </div>
      {actions !== undefined && (
        <div className="page-header__actions">{actions}</div>
      )}
    </div>
  )
}
