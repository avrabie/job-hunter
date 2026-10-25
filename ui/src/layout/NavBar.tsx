import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { hasRole } from '../admin/roles.ts'
import { useAuth } from '../auth/authContext.ts'

const navClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'nav__link nav__link--active' : 'nav__link'

const CV_PATHS = ['/export', '/publish']

/** The product mark: an inline SVG, which the Content-Security-Policy allows where an icon font would not load. */
export function BrandMark() {
  return (
    <svg
      className="brand__mark"
      viewBox="0 0 24 24"
      width="24"
      height="24"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="10.5" cy="10.5" r="6.5" fill="none" strokeWidth="2.5" />
      <path d="M15.5 15.5 21 21" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  )
}

/**
 * The signed-in top bar. NavLink marks the current item with `aria-current="page"` and matches by prefix, so
 * `/applications/123` highlights "Job tracking". Below 48rem the items sit behind the Menu button (CSS decides
 * the width; the state here only says whether the panel is open). CV is a disclosure with two links, the WAI
 * "disclosure navigation" pattern: a button with `aria-expanded`, no `menu` role and no arrow-key handling to learn.
 */
export function NavBar() {
  const auth = useAuth()
  const { pathname } = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [cvOpen, setCvOpen] = useState(false)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const cvButtonRef = useRef<HTMLButtonElement>(null)
  const cvItemRef = useRef<HTMLLIElement>(null)
  const [seenPath, setSeenPath] = useState(pathname)
  const [closedByNavigation, setClosedByNavigation] = useState(0)

  // A followed link closes both panels (adjusted during render, React's pattern for state that follows a value);
  // focus goes back to the Menu button so a phone user is not left on a link that has just disappeared.
  if (pathname !== seenPath) {
    setSeenPath(pathname)
    setCvOpen(false)
    if (menuOpen) {
      setMenuOpen(false)
      setClosedByNavigation((n) => n + 1)
    }
  }

  useEffect(() => {
    if (closedByNavigation > 0) toggleRef.current?.focus()
  }, [closedByNavigation])

  useEffect(() => {
    if (!cvOpen) return
    const outside = (e: PointerEvent) => {
      if (!cvItemRef.current?.contains(e.target as Node)) setCvOpen(false)
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [cvOpen])

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key !== 'Escape') return
    if (cvOpen) {
      setCvOpen(false)
      cvButtonRef.current?.focus()
    } else if (menuOpen) {
      setMenuOpen(false)
      toggleRef.current?.focus()
    }
  }

  const profile =
    auth.state.status === 'signedIn' ? auth.state.user.profile : null
  const identity = profile?.email ?? profile?.name ?? 'your account'
  const cvActive = CV_PATHS.some(
    (p) => pathname === p || pathname.startsWith(`${p}/`),
  )

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link className="brand" to="/">
          <BrandMark />
          <span>Job Hunter</span>
        </Link>
        <nav aria-label="Main" className="nav" onKeyDown={onKeyDown}>
          <button
            ref={toggleRef}
            type="button"
            className="nav__toggle"
            aria-expanded={menuOpen}
            aria-controls="main-menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <svg
              viewBox="0 0 24 24"
              width="20"
              height="20"
              aria-hidden="true"
              focusable="false"
            >
              <path
                d={
                  menuOpen ? 'M6 6l12 12M18 6 6 18' : 'M4 7h16M4 12h16M4 17h16'
                }
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
            Menu
          </button>
          <div
            id="main-menu"
            className={menuOpen ? 'nav__panel nav__panel--open' : 'nav__panel'}
          >
            <ul className="nav__links">
              <li>
                <NavLink className={navClass} to="/postings">
                  Postings
                </NavLink>
              </li>
              <li>
                <NavLink className={navClass} to="/matches">
                  Matches
                </NavLink>
              </li>
              <li>
                <NavLink className={navClass} to="/applications">
                  Job tracking
                </NavLink>
              </li>
              <li
                ref={cvItemRef}
                className="nav__item--submenu"
                onBlur={(e) => {
                  if (
                    !e.currentTarget.contains(e.relatedTarget as Node | null)
                  ) {
                    setCvOpen(false)
                  }
                }}
              >
                <button
                  ref={cvButtonRef}
                  type="button"
                  className={
                    cvActive ? 'nav__link nav__link--active' : 'nav__link'
                  }
                  aria-expanded={cvOpen}
                  aria-controls="cv-menu"
                  onClick={() => setCvOpen((open) => !open)}
                >
                  CV
                  <svg
                    className="nav__chevron"
                    viewBox="0 0 24 24"
                    width="16"
                    height="16"
                    aria-hidden="true"
                    focusable="false"
                  >
                    <path d="m6 9 6 6 6-6" strokeWidth="2" fill="none" />
                  </svg>
                </button>
                <ul id="cv-menu" className="nav__submenu" hidden={!cvOpen}>
                  <li>
                    <NavLink className={navClass} to="/export">
                      Export
                    </NavLink>
                  </li>
                  <li>
                    <NavLink className={navClass} to="/publish">
                      Public page
                    </NavLink>
                  </li>
                </ul>
              </li>
              <li>
                <NavLink className={navClass} to="/profile">
                  Profile
                </NavLink>
              </li>
              {hasRole(auth.getAccessToken(), 'ADMIN') && (
                <li>
                  <NavLink className={navClass} to="/admin">
                    Admin
                  </NavLink>
                </li>
              )}
            </ul>
            <div className="nav__account">
              <span className="nav__identity">
                <span className="visually-hidden">Signed in as </span>
                <span>{identity}</span>
              </span>
              <button
                type="button"
                className="button--secondary"
                onClick={() => void auth.signOut()}
              >
                Sign out
              </button>
            </div>
          </div>
        </nav>
      </div>
    </header>
  )
}
