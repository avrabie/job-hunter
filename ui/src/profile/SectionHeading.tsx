import type { ProfileSectionName } from '../cv/cvApi.ts'

export interface FillState {
  loading: boolean
  error: string | null
}

/** A section heading with its "Fill from CV" button, status and hint. */
export function SectionHeading({
  section,
  title,
  canFillFromCv,
  onFill,
  state,
}: {
  section: ProfileSectionName
  title: string
  /** Whether the user has a master CV; without one the button is disabled. */
  canFillFromCv: boolean
  onFill: () => void
  state: FillState
}) {
  return (
    <div className="section-heading">
      <h2 id={`${section}-heading`}>{title}</h2>
      <button
        type="button"
        className="secondary"
        disabled={!canFillFromCv || state.loading}
        onClick={onFill}
      >
        Fill from CV
      </button>
      {!canFillFromCv && (
        <p className="hint">Choose a master CV in your files to use this.</p>
      )}
      {state.loading && <p role="status">Reading your CV…</p>}
      {state.error && <p role="alert">{state.error}</p>}
    </div>
  )
}
