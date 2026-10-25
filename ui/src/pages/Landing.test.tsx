import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { AuthContext, type AuthApi } from '../auth/authContext.ts'
import { fakeAuth } from '../test/fakeAuth.ts'
import { Landing } from './Landing.tsx'
import { CAPABILITIES, PRIVACY_POINTS, STEPS } from './landingContent.ts'

function mount(auth: AuthApi, ui = <Landing returnTo="/matches" />) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthContext.Provider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('Landing', () => {
  it('has a hero with one h1, a one-sentence lead, Sign in and Register', async () => {
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    const hero = screen.getByRole('region', {
      name: 'Find Swiss software jobs that fit you',
    })
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(
      within(hero).getByText(/^Job Hunter matches .*review\.$/),
    ).toBeInTheDocument()
    expect(
      within(hero).getByRole('link', { name: 'Register' }),
    ).toHaveAttribute('href', '/register')
    await userEvent.click(within(hero).getByRole('button', { name: 'Sign in' }))
    expect(auth.signIn).toHaveBeenCalledWith('/matches')
  })

  it('shows six capability cards, from job matching to importing a job', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const section = within(
      screen.getByRole('region', { name: 'What you can do' }),
    )
    const cards = section.getAllByRole('listitem')
    expect(cards).toHaveLength(6)
    expect(
      section.getAllByRole('heading', { level: 3 }).map((h) => h.textContent),
    ).toEqual([
      'Find and match Swiss software jobs',
      'Job tracking',
      'Motivation letters',
      'CV export',
      'Public CV page',
      'Import a job from a URL',
    ])
    expect(cards.map((c) => c.textContent)).toEqual(
      CAPABILITIES.map((c) => `${c.title}${c.text}`),
    )
  })

  it('explains the three steps in order', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const steps = within(
      screen.getByRole('region', { name: 'How it works' }),
    ).getAllByRole('listitem')
    expect(steps).toHaveLength(3)
    expect(steps.map((s) => s.querySelector('h3')?.textContent)).toEqual([
      'Build your profile',
      'Find and track jobs',
      'Draft and export',
    ])
    expect(STEPS[2].text).toMatch(/You review everything/)
  })

  it('says the user reviews everything and nothing is sent to employers', () => {
    mount(fakeAuth({ status: 'signedOut' }))
    const privacy = within(
      screen.getByRole('region', { name: 'You stay in control' }),
    )
    expect(privacy.getAllByRole('listitem')).toHaveLength(PRIVACY_POINTS.length)
    expect(
      privacy.getByText(
        'You review everything. Nothing is ever sent to employers automatically.',
      ),
    ).toBeInTheDocument()
    expect(
      privacy.getByText('Your public CV is visible only if you publish it.'),
    ).toBeInTheDocument()
  })

  it('ends with a call to action that signs in or registers', async () => {
    const auth = fakeAuth({ status: 'signedOut' })
    mount(auth)
    const cta = within(screen.getByRole('region', { name: 'Ready to start?' }))
    expect(
      cta.getByRole('link', { name: 'Create an account' }),
    ).toHaveAttribute('href', '/register')
    await userEvent.click(
      cta.getByRole('button', { name: 'Sign in to get started' }),
    )
    expect(auth.signIn).toHaveBeenCalledWith('/matches')
  })

  it('is what a signed-out visitor sees at / and makes no fetch call', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const auth = fakeAuth({ status: 'signedOut' })
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    const main = within(screen.getByRole('main'))
    expect(
      main.getByRole('heading', {
        level: 1,
        name: 'Find Swiss software jobs that fit you',
      }),
    ).toBeInTheDocument()
    expect(main.getByRole('region', { name: 'How it works' })).toBeVisible()
    await userEvent.click(
      main.getByRole('button', { name: 'Sign in to get started' }),
    )
    expect(auth.signIn).toHaveBeenCalledWith(undefined)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
