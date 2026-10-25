import { useId, type ReactNode } from 'react'

export interface ControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
}

/**
 * A label, a control, an optional hint and an error, wired with `id`, `aria-describedby` and `aria-invalid`. The
 * control comes from `children`, which receives the attributes to spread on it. The profile form keeps its own
 * `profile/fields.tsx`, whose label-wraps-input markup its tests rely on.
 */
export function FormField({
  label,
  hint,
  error,
  children,
}: {
  label: ReactNode
  hint?: ReactNode
  error?: string | undefined
  children: (control: ControlProps) => ReactNode
}) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy =
    [hint !== undefined && hintId, error && errorId]
      .filter(Boolean)
      .join(' ') || undefined
  return (
    <div className={error ? 'form-field form-field--invalid' : 'form-field'}>
      <label className="form-field__label" htmlFor={id}>
        {label}
      </label>
      {children({
        id,
        ...(describedBy && { 'aria-describedby': describedBy }),
        ...(error && { 'aria-invalid': true as const }),
      })}
      {hint !== undefined && (
        <p id={hintId} className="form-field__hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="form-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
