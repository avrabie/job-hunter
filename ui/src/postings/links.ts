/** The URL if it is an http or https address, else null. Anything else (`javascript:`, `data:`, junk) is never linked. */
export function safeHttpUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
      ? url.href
      : null
  } catch {
    return null
  }
}
