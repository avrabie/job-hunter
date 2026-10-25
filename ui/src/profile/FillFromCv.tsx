export interface FillState {
  loading: boolean
  error: string | null
}

/**
 * The "Fill from CV" button of a section, with its status and hint. The section's heading sits in the summary line of
 * its collapsible section, where a button does not belong, so the control is the first thing inside the section.
 */
export function FillFromCv({
  canFillFromCv,
  onFill,
  state,
}: {
  /** Whether the user has a master CV; without one the button is disabled. */
  canFillFromCv: boolean
  onFill: () => void
  state: FillState
}) {
  return (
    <div className="fill-from-cv">
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
      {state.error && <Alert tone="danger">{state.error}</Alert>}
    </div>
  )
}
import { Alert } from '../ui/Alert.tsx'
