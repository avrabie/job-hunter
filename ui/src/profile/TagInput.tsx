import { useId, useState, type KeyboardEvent } from 'react'
import { tidyTags } from './types.ts'

interface Props {
  label: string
  value: string[]
  onChange: (tags: string[]) => void
  /** Names offered while typing, such as the skills and the tags used elsewhere. */
  suggestions: string[]
  /** Field path, so the form can tell which field was left or should get focus. */
  path: string
  /** The server's message for this field, shown underneath. */
  error?: string | undefined
}

/**
 * Tags as chips. Enter or a comma adds what is typed, Backspace in the empty input removes the last tag, and each chip
 * has its own remove control. A tag that differs from an existing one only in case is not added.
 */
export function TagInput({
  label,
  value,
  onChange,
  suggestions,
  path,
  error,
}: Props) {
  const [draft, setDraft] = useState('')
  const inputId = useId()
  const listId = useId()
  const offered = tidyTags(suggestions).filter(
    (s) => !value.some((v) => v.toLowerCase() === s.toLowerCase()),
  )

  function add() {
    const text = draft.trim()
    if (text !== '') onChange(tidyTags([...value, text]))
    setDraft('')
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      // Enter must not submit the form, and a comma is a separator, not a character of the tag.
      e.preventDefault()
      add()
    } else if (e.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div>
      <label htmlFor={inputId}>{label}</label>
      {value.length > 0 && (
        <ul className="tag-list">
          {value.map((tag, i) => (
            <li key={tag}>
              {tag}
              <button
                type="button"
                className="secondary"
                aria-label={`Remove ${tag}`}
                onClick={() => onChange(value.filter((_, j) => j !== i))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        id={inputId}
        data-path={path}
        list={listId}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={onKeyDown}
        // Text typed but not confirmed would be lost when the user goes on to Save, so leaving the field adds it.
        onBlur={add}
      />
      <datalist id={listId}>
        {offered.map((s) => (
          <option key={s} value={s} />
        ))}
      </datalist>
      {error && <span role="alert">{`${label}: ${error}`}</span>}
    </div>
  )
}
