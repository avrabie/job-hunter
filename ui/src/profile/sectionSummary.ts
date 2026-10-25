/** Summaries and defaults of the collapsible profile sections. */

/** A list section with more entries than this starts closed, so a long profile opens on an overview. */
export const LONG_LIST = 3

/** "0 positions", "1 position", "4 positions". */
export function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`
}

/** Like `plural`, with "None" for zero. */
export function countOf(n: number, one: string, many: string): string {
  return n === 0 ? 'None' : plural(n, one, many)
}

/** Whether any message is for a field under one of the path prefixes, such as `experience[` or `contact.`. */
export function hasErrorAt(
  errors: Record<string, string>,
  prefixes: string[],
): boolean {
  return Object.keys(errors).some((k) =>
    prefixes.some((p) => k === p || k.startsWith(p)),
  )
}
