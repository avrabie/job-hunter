import type { ReactNode } from 'react'

/**
 * A message box. Errors use `role="alert"` so screen readers announce them at once; the other tones use
 * `role="status"`, which waits for a pause.
 */
export function Alert({
  tone = 'info',
  title,
  children,
}: {
  tone?: 'info' | 'success' | 'warning' | 'danger'
  title?: ReactNode
  children: ReactNode
}) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={`alert alert--${tone}`}
    >
      {title !== undefined && <p className="alert__title">{title}</p>}
      <div className="alert__body">{children}</div>
    </div>
  )
}
