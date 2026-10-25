/** Attributes that tie a control to its error message; spread them on the control. */
export interface FieldControl {
  'aria-describedby'?: string
  'aria-invalid'?: true
}

/** The attributes for a control whose error message has the id `errorId`. */
export function errorAttributes(
  errorId: string,
  error: string | undefined,
): FieldControl {
  return error
    ? { 'aria-describedby': errorId, 'aria-invalid': true as const }
    : {}
}
