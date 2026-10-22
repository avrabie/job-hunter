import { safeHttpUrl } from './links.ts'
import type { PostingLink } from './types.ts'

/** One "Open original" link per source. Only http and https addresses become links. */
export function OriginalLinks({ links }: { links: PostingLink[] }) {
  const safe = links.flatMap((link) => {
    const href = safeHttpUrl(link.url)
    return href ? [{ href, sourceId: link.sourceId }] : []
  })
  if (safe.length === 0) return null
  return (
    <ul className="original-links">
      {safe.map(({ href, sourceId }) => (
        <li key={`${sourceId}-${href}`}>
          <a href={href} target="_blank" rel="noopener noreferrer">
            Open original ({sourceId})
          </a>
        </li>
      ))}
    </ul>
  )
}
