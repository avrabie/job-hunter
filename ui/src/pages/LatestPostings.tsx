import { useEffect, useId, useState } from 'react'
import {
  firstSeenAgo,
  loadPublicPostings,
  type PublicPosting,
} from '../postings/publicPostingsApi.ts'

/**
 * The latest public postings on the landing page: title, company, city and age as plain text, each with a sign-in
 * action. Absent while loading and when the call fails, is limited or returns nothing. No description is shown.
 */
export function LatestPostings({ onSignIn }: { onSignIn: () => void }) {
  const headingId = useId()
  const [postings, setPostings] = useState<PublicPosting[]>([])

  useEffect(() => {
    const controller = new AbortController()
    void loadPublicPostings(controller.signal).then((items) => {
      if (!controller.signal.aborted) setPostings(items)
    })
    return () => controller.abort()
  }, [])

  if (postings.length === 0) return null
  return (
    <section
      className="landing__section landing__postings"
      aria-labelledby={headingId}
    >
      <h2 id={headingId} className="landing__heading">
        Latest postings
      </h2>
      <ul className="landing__posting-list">
        {postings.map((p) => {
          const ago = firstSeenAgo(p.firstSeen)
          return (
            <li key={p.id} className="card landing__posting">
              <h3 className="card__title">{p.title}</h3>
              <p className="landing__posting-meta">
                {[p.company, p.city, ago].filter(Boolean).join(' · ')}
              </p>
              <button
                type="button"
                className="button--secondary"
                aria-label={`Sign in to read more: ${p.title}`}
                onClick={onSignIn}
              >
                Sign in to read more
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
