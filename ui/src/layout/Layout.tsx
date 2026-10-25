import type { MouseEvent } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/authContext.ts'
import { Footer } from './Footer.tsx'
import { BrandMark, NavBar } from './NavBar.tsx'

/**
 * The first tab stop. It focuses `main` itself instead of following `#content`, so the address keeps no fragment
 * and the router sees no navigation.
 */
function SkipLink() {
  const skip = (e: MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault()
    document.getElementById('content')?.focus()
  }
  return (
    <a className="skip-link" href="#content" onClick={skip}>
      Skip to content
    </a>
  )
}

/**
 * Brand, Sign in and Register for visitors and for the moments before the sign-in state is known. While the state
 * loads the two actions stay hidden, so a signed-in user reloading a page does not see them flash.
 */
function PublicHeader() {
  const { state, signIn } = useAuth()
  const { pathname } = useLocation()
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link className="brand" to="/">
          <BrandMark />
          <span>Job Hunter</span>
        </Link>
        {state.status !== 'loading' && (
          <nav aria-label="Account" className="nav nav--public">
            <button type="button" onClick={() => void signIn(pathname)}>
              Sign in
            </button>
            <Link className="button button--secondary" to="/register">
              Register
            </Link>
          </nav>
        )}
      </div>
    </header>
  )
}

/**
 * The frame of every page: skip link, a header that depends on the sign-in state, the page, and the footer (with
 * the service status when signed in). `main` stays at the same place in the tree when the state changes, so the
 * page inside keeps its state: `/callback` must not start over when the sign-in completes. The pages still decide
 * what a signed-out visitor sees.
 */
export function Layout() {
  const { state } = useAuth()
  const signedIn = state.status === 'signedIn'
  return (
    <>
      <SkipLink />
      {signedIn ? <NavBar /> : <PublicHeader />}
      {/* tabIndex -1: the skip link can focus it without making it a tab stop. */}
      <main id="content" className="site-main" tabIndex={-1}>
        <Outlet />
      </main>
      <Footer signedIn={signedIn} />
    </>
  )
}
