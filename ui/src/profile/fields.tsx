import type { ReactNode } from 'react'

/** A labelled control with the server's message for its field underneath. */
export function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string | undefined
  children: ReactNode
}) {
  return (
    <label>
      {label}
      {children}
      {error && <span role="alert">{`${label}: ${error}`}</span>}
    </label>
  )
}

export function TextInput({
  label,
  value,
  onChange,
  error,
  type = 'text',
}: {
  label: string
  value: string | null
  onChange: (value: string) => void
  error?: string | undefined
  type?: 'text' | 'month'
}) {
  return (
    <Field label={label} error={error}>
      <input
        type={type}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
      />
    </Field>
  )
}

export function NumberInput({
  label,
  value,
  onChange,
  error,
}: {
  label: string
  value: number | null
  onChange: (value: number | null) => void
  error?: string | undefined
}) {
  return (
    <Field label={label} error={error}>
      <input
        type="number"
        inputMode="numeric"
        value={value ?? ''}
        onChange={(e) => {
          const n = e.target.valueAsNumber
          onChange(Number.isNaN(n) ? null : n)
        }}
      />
    </Field>
  )
}

export function Select<T extends string>({
  label,
  value,
  options,
  onChange,
  error,
  empty,
}: {
  label: string
  value: T | null
  options: readonly T[]
  onChange: (value: T | null) => void
  error?: string | undefined
  /** Text of the "nothing chosen" option; without it a value is required. */
  empty?: string
}) {
  return (
    <Field label={label} error={error}>
      <select
        value={value ?? ''}
        onChange={(e) =>
          onChange(e.target.value === '' ? null : (e.target.value as T))
        }
      >
        {empty !== undefined && <option value="">{empty}</option>}
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    </Field>
  )
}

function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length) return items
  const next = [...items]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item as T)
  return next
}

/** A list of entries the user can add to, remove from and reorder. */
export function Repeater<T>({
  title,
  items,
  onChange,
  blank,
  children,
}: {
  title: string
  items: T[]
  onChange: (items: T[]) => void
  blank: () => T
  children: (
    item: T,
    set: (patch: Partial<T>) => void,
    index: number,
  ) => ReactNode
}) {
  return (
    <div>
      {items.map((item, i) => (
        <fieldset key={i}>
          <legend>{`${title} ${i + 1}`}</legend>
          {children(
            item,
            (patch) =>
              onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x))),
            i,
          )}
          <div className="actions compact">
            <button
              type="button"
              className="secondary"
              aria-label={`Move ${title} ${i + 1} up`}
              disabled={i === 0}
              onClick={() => onChange(moveItem(items, i, i - 1))}
            >
              Up
            </button>
            <button
              type="button"
              className="secondary"
              aria-label={`Move ${title} ${i + 1} down`}
              disabled={i === items.length - 1}
              onClick={() => onChange(moveItem(items, i, i + 1))}
            >
              Down
            </button>
            <button
              type="button"
              className="secondary"
              aria-label={`Remove ${title} ${i + 1}`}
              onClick={() => onChange(items.filter((_, j) => j !== i))}
            >
              Remove
            </button>
          </div>
        </fieldset>
      ))}
      <button
        type="button"
        className="secondary"
        onClick={() => onChange([...items, blank()])}
      >
        {`Add ${title.toLowerCase()}`}
      </button>
    </div>
  )
}

/** A list of single text values (achievements, target roles, cantons). */
export function StringList({
  title,
  items,
  onChange,
  errors,
  path,
}: {
  title: string
  items: string[]
  onChange: (items: string[]) => void
  errors: Record<string, string>
  path: string
}) {
  return (
    <div>
      {items.map((value, i) => (
        <div key={i} className="row">
          <TextInput
            label={`${title} ${i + 1}`}
            value={value}
            error={errors[`${path}[${i}]`]}
            onChange={(v) => onChange(items.map((x, j) => (j === i ? v : x)))}
          />
          <button
            type="button"
            className="secondary"
            aria-label={`Remove ${title.toLowerCase()} ${i + 1}`}
            onClick={() => onChange(items.filter((_, j) => j !== i))}
          >
            Remove
          </button>
        </div>
      ))}
      <button
        type="button"
        className="secondary"
        onClick={() => onChange([...items, ''])}
      >
        {`Add ${title.toLowerCase()}`}
      </button>
    </div>
  )
}
