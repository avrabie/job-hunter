import type { Contact } from './types.ts'

/** The labels of the contact fields, the only names that appear in the confirmation. */
export const CONTACT_LABELS: Record<keyof Contact, string> = {
  fullName: 'Full name',
  headline: 'Headline',
  email: 'Email',
  phone: 'Phone',
  website: 'Website',
  linkedin: 'LinkedIn',
}

const FIELDS = Object.keys(CONTACT_LABELS) as (keyof Contact)[]

function blank(value: string | null): boolean {
  return value === null || value.trim() === ''
}

/** Whether the CV gave any contact value at all. */
export function hasContactValue(draft: Contact): boolean {
  return FIELDS.some((f) => !blank(draft[f]))
}

/**
 * The labels of the fields that hold a different non-empty value in the form than in the CV. Only these need the
 * user's confirmation; the values themselves are never shown.
 */
export function contactConflicts(current: Contact, draft: Contact): string[] {
  return FIELDS.filter(
    (f) => !blank(current[f]) && !blank(draft[f]) && current[f] !== draft[f],
  ).map((f) => CONTACT_LABELS[f])
}

/**
 * The form's contact after a CV fill: an empty field takes the CV value; a field with a different value takes it only
 * when `replaceConflicts` is set. Equal values and empty CV values change nothing.
 */
export function applyContactFill(
  current: Contact,
  draft: Contact,
  replaceConflicts: boolean,
): Contact {
  const next = { ...current }
  for (const f of FIELDS) {
    if (blank(draft[f])) continue
    if (blank(current[f]) || (replaceConflicts && current[f] !== draft[f]))
      next[f] = draft[f]
  }
  return next
}

/** How many fields the fill changed. */
export function countChanged(before: Contact, after: Contact): number {
  return FIELDS.filter((f) => before[f] !== after[f]).length
}
