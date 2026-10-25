import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { Layout } from './Layout.tsx'

const tokenWith = (roles: string[]) =>
  `h.${btoa(JSON.stringify({ roles })).replace(/=+$/, '')}.s`

function Page({ title }: { title: string }) {
  return (
    <div>
      <h1>{title}</h1>
      <Link to="/postings">Go to postings</Link>
    </div>
  )
}

function mount(path: string, auth: AuthApi = fakeAuth(signedIn())) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<Layout />}>
            <Route path="*" element={<Page title="Some page" />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

const mainNav = () => within(screen.getByRole('navigation', { name: 'Main' }))

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('x', { status: 404 })),
  )
})

afterEach(() => vi.unstubAllGlobals())

describe('skip link', () => {
  it('is the first tab stop and moves focus to the main content', async () => {
    mount('/postings')
    await userEvent.tab()
    const skip = screen.getByRole('link', { name: 'Skip to content' })
    expect(skip).toHaveFocus()
    await userEvent.click(skip)
    expect(screen.getByRole('main')).toHaveFocus()
    expect(screen.getByRole('main')).toHaveAttribute('id', 'content')
  })
})

describe('current page', () => {
  it.each([
    ['/postings', 'Postings'],
    ['/postings/42', 'Postings'],
    ['/matches', 'Matches'],
    ['/applications', 'Job tracking'],
    ['/applications/123', 'Job tracking'],
    ['/profile', 'Profile'],
  ])('on %s marks %s with aria-current', (path, name) => {
    mount(path)
    const current = mainNav()
      .getAllByRole('link')
      .filter((l) => l.getAttribute('aria-current') === 'page')
    expect(current.map((l) => l.textContent)).toEqual([name])
  })

  it('marks the CV button and the Export link on /export', async () => {
    mount('/export')
    const cv = mainNav().getByRole('button', { name: 'CV' })
    expect(cv).toHaveClass('nav__link--active')
    await userEvent.click(cv)
    expect(mainNav().getByRole('link', { name: 'Export' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(
      mainNav().getByRole('link', { name: 'Public page' }),
    ).not.toHaveAttribute('aria-current')
  })
})

describe('admin link', () => {
  it('is shown to an administrator', () => {
    mount(
      '/',
      fakeAuth(signedIn(), { getAccessToken: () => tokenWith(['ADMIN']) }),
    )
    expect(mainNav().getByRole('link', { name: 'Admin' })).toHaveAttribute(
      'href',
      '/admin',
    )
  })

  it('is not rendered without the ADMIN role', () => {
    mount(
      '/',
      fakeAuth(signedIn(), { getAccessToken: () => tokenWith(['USER']) }),
    )
    expect(mainNav().queryByRole('link', { name: 'Admin' })).toBeNull()
  })
})

describe('identity and sign out', () => {
  it('shows the email from the ID token and signs out', async () => {
    const auth = fakeAuth(signedIn())
    mount('/', auth)
    expect(mainNav().getByText('ada@example.ch')).toBeInTheDocument()
    await userEvent.click(mainNav().getByRole('button', { name: 'Sign out' }))
    expect(auth.signOut).toHaveBeenCalledTimes(1)
    await screen.findByText('The backend has no endpoints yet.')
  })
})

describe('CV menu', () => {
  it('opens and closes with its button and closes on Escape with focus back on the button', async () => {
    mount('/')
    const cv = mainNav().getByRole('button', { name: 'CV' })
    expect(cv).toHaveAttribute('aria-expanded', 'false')
    expect(cv).toHaveAttribute('aria-controls', 'cv-menu')
    expect(mainNav().queryByRole('link', { name: 'Export' })).toBeNull()
    await userEvent.click(cv)
    expect(cv).toHaveAttribute('aria-expanded', 'true')
    expect(mainNav().getByRole('link', { name: 'Export' })).toHaveAttribute(
      'href',
      '/export',
    )
    expect(
      mainNav().getByRole('link', { name: 'Public page' }),
    ).toHaveAttribute('href', '/publish')
    await userEvent.tab()
    expect(mainNav().getByRole('link', { name: 'Export' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(cv).toHaveAttribute('aria-expanded', 'false')
    expect(cv).toHaveFocus()
  })

  it('closes when a link in it is followed', async () => {
    mount('/')
    await userEvent.click(mainNav().getByRole('button', { name: 'CV' }))
    await userEvent.click(mainNav().getByRole('link', { name: 'Public page' }))
    expect(mainNav().getByRole('button', { name: 'CV' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  it('closes when focus leaves it', async () => {
    mount('/')
    const cv = mainNav().getByRole('button', { name: 'CV' })
    await userEvent.click(cv)
    await userEvent.click(mainNav().getByRole('link', { name: 'Profile' }))
    expect(cv).toHaveAttribute('aria-expanded', 'false')
  })
})

describe('menu button for narrow screens', () => {
  it('controls the panel and says whether it is open', async () => {
    mount('/')
    const toggle = mainNav().getByRole('button', { name: 'Menu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveAttribute('aria-controls', 'main-menu')
    const panel = document.getElementById('main-menu')!
    expect(panel).not.toHaveClass('nav__panel--open')
    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(panel).toHaveClass('nav__panel--open')
    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('closes on Escape and returns focus to the button', async () => {
    mount('/')
    const toggle = mainNav().getByRole('button', { name: 'Menu' })
    await userEvent.click(toggle)
    await userEvent.tab()
    expect(mainNav().getByRole('link', { name: 'Postings' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
  })

  it('closes on navigation and returns focus to the button', async () => {
    mount('/')
    const toggle = mainNav().getByRole('button', { name: 'Menu' })
    await userEvent.click(toggle)
    await userEvent.click(mainNav().getByRole('link', { name: 'Matches' }))
    await waitFor(() =>
      expect(toggle).toHaveAttribute('aria-expanded', 'false'),
    )
    expect(toggle).toHaveFocus()
    expect(mainNav().getByRole('link', { name: 'Matches' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('also closes when the page itself navigates', async () => {
    mount('/')
    const toggle = mainNav().getByRole('button', { name: 'Menu' })
    await userEvent.click(toggle)
    await userEvent.click(screen.getByRole('link', { name: 'Go to postings' }))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })
})

describe('signed-out header', () => {
  it('signs in with the current page as the place to return to', async () => {
    const auth = fakeAuth({ status: 'signedOut' })
    mount('/profile', auth)
    const header = within(screen.getByRole('banner'))
    await userEvent.click(header.getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledWith('/profile')
    expect(header.getByRole('link', { name: 'Register' })).toHaveAttribute(
      'href',
      '/register',
    )
    expect(fetch).not.toHaveBeenCalled()
  })

  it('keeps the page mounted when the sign-in completes', async () => {
    const Counting = () => <h1>Callback</h1>
    const view = (auth: AuthApi) => (
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/callback']}>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/callback" element={<Counting />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    )
    const { rerender } = render(view(fakeAuth({ status: 'loading' })))
    const heading = screen.getByRole('heading', { name: 'Callback' })
    rerender(view(fakeAuth(signedIn())))
    expect(screen.getByRole('heading', { name: 'Callback' })).toBe(heading)
    await screen.findByText('The backend has no endpoints yet.')
  })
})
