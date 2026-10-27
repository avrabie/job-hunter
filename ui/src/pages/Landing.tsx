import { useId, type ReactNode } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { LatestPostings } from './LatestPostings.tsx'
import {
  CAPABILITIES,
  HERO,
  PRIVACY_POINTS,
  STEPS,
  type IconName,
} from './landingContent.ts'

/** Stroke-only shapes on a 24 px grid; the colour comes from CSS (`currentColor`), so they follow the theme. */
const ICON_SHAPES: Record<IconName, ReactNode> = {
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M15.5 15.5 21 21" />
    </>
  ),
  list: (
    <>
      <path d="M9 6h11M9 12h11M9 18h11" />
      <path d="m3.5 6 1.5 1.5L7.5 5M3.5 12l1.5 1.5 2.5-2.5M3.5 18h3" />
    </>
  ),
  letter: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3.5 6.5 8.5 6.5 8.5-6.5" />
    </>
  ),
  document: (
    <>
      <path d="M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z" />
      <path d="M14 3v5h5M8.5 13h7M8.5 17h7" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9" />
    </>
  ),
  link: (
    <>
      <path d="M10 14a4 4 0 0 0 5.7 0l3.1-3.1a4 4 0 0 0-5.7-5.7L12 6.3" />
      <path d="M14 10a4 4 0 0 0-5.7 0l-3.1 3.1a4 4 0 0 0 5.7 5.7l1.1-1.1" />
    </>
  ),
}

function Icon({ name }: { name: IconName }) {
  return (
    <svg
      className="landing__icon"
      viewBox="0 0 24 24"
      width="24"
      height="24"
      aria-hidden="true"
      focusable="false"
    >
      {ICON_SHAPES[name]}
    </svg>
  )
}

/** A landing section named by its `h2`, so each one is a region in the landmarks list. */
function Section({
  title,
  className,
  children,
}: {
  title: string
  className?: string
  children: ReactNode
}) {
  const headingId = useId()
  return (
    <section
      className={
        className ? `landing__section ${className}` : 'landing__section'
      }
      aria-labelledby={headingId}
    >
      <h2 id={headingId} className="landing__heading">
        {title}
      </h2>
      {children}
    </section>
  )
}

/**
 * What a signed-out visitor sees; `returnTo` is the page to open after signing in. It shows no user data and makes
 * one call to the API, the public latest postings without a token; if that call fails the section is absent and
 * the page works while the backend is down.
 */
export function Landing({ returnTo }: { returnTo?: string }) {
  const { signIn } = useAuth()
  const heroId = useId()
  return (
    <div className="page landing">
      <section className="landing__hero" aria-labelledby={heroId}>
        <h1 id={heroId}>{HERO.title}</h1>
        <p className="landing__lead">{HERO.lead}</p>
        <div className="landing__actions">
          <button type="button" onClick={() => void signIn(returnTo)}>
            Sign in
          </button>
          <Link className="button button--secondary" to="/register">
            Register
          </Link>
        </div>
      </section>

      <LatestPostings onSignIn={() => void signIn(returnTo)} />

      <Section title="What you can do">
        <ul className="landing__capabilities">
          {CAPABILITIES.map((c) => (
            <li key={c.title} className="card landing__capability">
              <span className="landing__icon-box">
                <Icon name={c.icon} />
              </span>
              <h3 className="card__title">{c.title}</h3>
              <p>{c.text}</p>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="How it works">
        <ol className="landing__steps">
          {STEPS.map((s, i) => (
            <li key={s.title} className="landing__step">
              <span className="landing__step-number" aria-hidden="true">
                {i + 1}
              </span>
              <div>
                <h3 className="landing__step-title">{s.title}</h3>
                <p>{s.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </Section>

      <Section title="You stay in control" className="landing__privacy">
        <ul className="landing__points">
          {PRIVACY_POINTS.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      </Section>

      <Section title="Ready to start?" className="landing__cta">
        <p>Sign in with your account, or register to create one.</p>
        {/* Names differ from the hero buttons, so each control has one clear accessible name on the page. */}
        <div className="landing__actions">
          <button type="button" onClick={() => void signIn(returnTo)}>
            Sign in to get started
          </button>
          <Link className="button button--secondary" to="/register">
            Create an account
          </Link>
        </div>
      </Section>
    </div>
  )
}
