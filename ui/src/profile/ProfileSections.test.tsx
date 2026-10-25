import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import fixture from './fixtures/full-profile.json'
import { ProfilePage } from './ProfilePage.tsx'
import {
  blankExperience,
  emptyProfile,
  type Profile,
  type Skill,
  type SkillLevel,
} from './types.ts'

/*
 * The collapsible sections of the profile page and the grouped Skills section. The editing behaviour itself is pinned
 * by ProfilePage.test.tsx; these tests check that collapsing and grouping are display only. All data is synthetic.
 */

const full = fixture as unknown as Profile

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

/** Answers GET with `stored` and PUT with `save` (by default: saved as sent); records the PUT bodies. */
function stubApi(
  stored: Profile,
  save?: (profile: Profile) => Response,
): Profile[] {
  const puts: Profile[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/files') return json(200, [])
      if (url.startsWith('/api/files') || url === '/api/cv/public')
        return new Response('', { status: 404 })
      if ((init?.method ?? 'GET') === 'GET')
        return json(200, { version: 1, profile: stored })
      const { profile } = JSON.parse(init!.body as string) as {
        profile: Profile
      }
      puts.push(profile)
      return save?.(profile) ?? json(200, { version: 2, profile })
    }),
  )
  return puts
}

async function mount() {
  const view = render(
    <AuthContext.Provider value={fakeAuth(signedIn())}>
      <MemoryRouter initialEntries={['/profile']}>
        <ProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  await screen.findByLabelText('City')
  return view
}

/** The `details` of a section or skill group, found by its heading. */
const details = (heading: string) =>
  screen
    .getByRole('heading', { name: heading })
    .closest('details') as HTMLDetailsElement

const summaryOf = (heading: string) =>
  details(heading).querySelector('.collapsible__meta')?.textContent

const save = async () => {
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
}

const skill = (name: string, level: SkillLevel | null = null): Skill => ({
  name,
  level,
  origin: 'MANUAL',
  sources: [],
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('collapsible profile sections', () => {
  it('shows every section as a collapsible section with a one-line summary', async () => {
    stubApi(full)
    await mount()
    expect(summaryOf('Contact')).toBe('Ada Example')
    expect(summaryOf('About you')).toBe('Zurich, ZH')
    expect(summaryOf('Languages')).toBe('2 languages')
    expect(summaryOf('Experience')).toBe('2 positions')
    expect(summaryOf('Education')).toBe('1 entry')
    expect(summaryOf('Publications')).toBe('1 publication')
    expect(summaryOf('Open Source Projects')).toBe('1 project · 1 account')
    expect(summaryOf('Skills')).toBe('3 skills')
    expect(summaryOf('Preferences')).toBe('2 target roles')
    for (const name of ['Contact', 'Experience', 'Skills', 'Preferences'])
      expect(details(name).open).toBe(true)
  })

  it('says "None" and "Not set" for an empty profile', async () => {
    stubApi(emptyProfile())
    await mount()
    expect(summaryOf('Contact')).toBe('Not set')
    expect(summaryOf('Experience')).toBe('None')
    expect(summaryOf('Open Source Projects')).toBe('None')
    expect(summaryOf('Preferences')).toBe('Not set')
  })

  it('starts a section with many entries closed and the short ones open', async () => {
    const experience = ['A', 'B', 'C', 'D'].map((employer) => ({
      ...blankExperience(),
      employer: `${employer} AG`,
      title: 'Engineer',
      start: '2020-01',
    }))
    stubApi({ ...emptyProfile(), experience })
    await mount()
    expect(details('Experience').open).toBe(false)
    expect(summaryOf('Experience')).toBe('4 positions')
    expect(details('Contact').open).toBe(true)
    expect(details('Education').open).toBe(true)
  })

  it('collapses and expands all sections', async () => {
    stubApi(full)
    await mount()
    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }))
    for (const name of ['Contact', 'About you', 'Experience', 'Skills'])
      expect(details(name).open).toBe(false)
    expect(screen.getByLabelText('City')).not.toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Expand all' }))
    for (const name of ['Contact', 'About you', 'Experience', 'Skills'])
      expect(details(name).open).toBe(true)
    expect(screen.getByLabelText('City')).toBeVisible()
  })

  it('saves the same profile with the sections collapsed as expanded', async () => {
    const expanded = stubApi(full)
    const first = await mount()
    await save()
    await screen.findByText('Saved (version 2).')
    first.unmount()

    const collapsed = stubApi(full)
    await mount()
    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }))
    await userEvent.click(screen.getByText('Experience'))
    await save()
    await screen.findByText('Saved (version 2).')

    expect(collapsed).toHaveLength(1)
    expect(collapsed[0]).toEqual(expanded[0])
  })

  it('opens the collapsed section of the first invalid field after a failed save and focuses it', async () => {
    stubApi(full, () =>
      json(400, {
        error: 'validation_failed',
        fields: {
          'contact.email': 'must be an email address',
          'preferences.workloadMin': 'must be at most 100',
        },
      }),
    )
    await mount()
    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }))
    expect(details('Contact').open).toBe(false)
    await save()

    await screen.findByText('Email: must be an email address')
    expect(details('Contact').open).toBe(true)
    expect(document.querySelector('[data-path="contact.email"]')).toHaveFocus()
    // The other section with a message opens too; sections without one stay as they were.
    expect(details('Preferences').open).toBe(true)
    expect(details('Experience').open).toBe(false)
    await new Promise((r) => setTimeout(r, 20))
    expect(details('Contact').open).toBe(true)
  })

  it('still warns about unsaved edits in a collapsed section', async () => {
    stubApi(full)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    await mount()
    await userEvent.type(screen.getByLabelText('City'), 'x')
    await userEvent.click(screen.getByText('About you'))
    await waitFor(() => expect(details('About you').open).toBe(false))

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    await userEvent.click(screen.getByRole('link', { name: 'Back' }))
    expect(confirm).toHaveBeenCalledTimes(1)
  })
})

describe('grouped skills', () => {
  const skills = () => within(screen.getByRole('region', { name: 'Skills' }))
  const inGroup = (label: string) => within(details(label))
  const names = (label: string) =>
    inGroup(label)
      .getAllByLabelText('Skill name')
      .map((el) => (el as HTMLInputElement).value)

  /** 21 skills: one past the limit at which the groups start closed. */
  const many = (): Skill[] => [
    skill('Kotlin', 'EXPERT'),
    skill('Spring Boot', 'ADVANCED'),
    skill('PostgreSQL', 'ADVANCED'),
    skill('Kafka'),
    ...Array.from({ length: 17 }, (_, i) => skill(`Skill ${i + 1}`)),
  ]

  it('puts Kotlin, Spring Boot, PostgreSQL and Kafka in their groups, and an unknown skill in Other', async () => {
    stubApi({
      ...emptyProfile(),
      skills: [
        skill('Kafka'),
        skill('Unknown thing'),
        skill('PostgreSQL'),
        skill('Spring Boot'),
        skill('Kotlin'),
      ],
    })
    await mount()
    expect(names('Programming languages')).toEqual(['Kotlin'])
    expect(names('Frameworks')).toEqual(['Spring Boot'])
    expect(names('Databases')).toEqual(['PostgreSQL'])
    expect(names('Messaging')).toEqual(['Kafka'])
    expect(names('Other')).toEqual(['Unknown thing'])
    expect(screen.queryByRole('heading', { name: 'Tools' })).toBeNull()
    // Groups follow the fixed order of the CV export.
    const order = skills()
      .getAllByRole('heading', { level: 3 })
      .map((h) => h.textContent)
    expect(order).toEqual([
      'Programming languages',
      'Frameworks',
      'Messaging',
      'Databases',
      'Other',
    ])
    expect(summaryOf('Messaging')).toBe('1 skill')
  })

  it('sorts a group by level, highest first and unset last, then by name', async () => {
    stubApi({
      ...emptyProfile(),
      skills: [
        skill('Java'),
        skill('Python', 'BEGINNER'),
        skill('Go', 'EXPERT'),
        skill('C#'),
      ],
    })
    await mount()
    expect(names('Programming languages')).toEqual([
      'Go',
      'Python',
      'C#',
      'Java',
    ])
  })

  it('starts the groups open up to 20 skills and closed above, each with its count', async () => {
    stubApi({ ...emptyProfile(), skills: many().slice(0, 20) })
    const first = await mount()
    expect(details('Other').open).toBe(true)
    first.unmount()

    stubApi({ ...emptyProfile(), skills: many() })
    await mount()
    for (const group of [
      'Programming languages',
      'Frameworks',
      'Messaging',
      'Databases',
      'Other',
    ])
      expect(details(group).open).toBe(false)
    expect(summaryOf('Other')).toBe('17 skills')
    expect(summaryOf('Programming languages')).toBe('1 skill')
    expect(details('Skills').open).toBe(true)
  })

  it('filters by name across the groups, hides groups with no match and opens the rest', async () => {
    stubApi({ ...emptyProfile(), skills: many() })
    await mount()
    await userEvent.type(skills().getByLabelText('Filter skills'), 'POST')

    expect(skills().getAllByLabelText('Skill name')).toHaveLength(1)
    expect(names('Databases')).toEqual(['PostgreSQL'])
    expect(details('Databases').open).toBe(true)
    expect(screen.queryByRole('heading', { name: 'Other' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Frameworks' })).toBeNull()

    await userEvent.clear(skills().getByLabelText('Filter skills'))
    await userEvent.type(skills().getByLabelText('Filter skills'), 'nothing')
    expect(skills().getByText('No skill matches "nothing".')).toBeVisible()
    await userEvent.clear(skills().getByLabelText('Filter skills'))
    expect(skills().getAllByLabelText('Skill name')).toHaveLength(21)
  })

  it('edits, removes and adds inside the groups and saves in the original order', async () => {
    const stored = [
      skill('Kafka'),
      skill('Kotlin', 'EXPERT'),
      skill('Unknown thing'),
      skill('PostgreSQL'),
    ]
    const puts = stubApi({ ...emptyProfile(), skills: stored })
    await mount()

    await userEvent.selectOptions(
      inGroup('Databases').getByLabelText('Skill level'),
      'ADVANCED',
    )
    await userEvent.click(
      inGroup('Other').getByRole('button', { name: 'Remove Skill 3' }),
    )
    await userEvent.click(skills().getByRole('button', { name: 'Add skill' }))
    // The new, empty skill lands in Other, open and with its name focused; typing does not move it away.
    const added = inGroup('Other').getByLabelText('Skill name')
    expect(added).toHaveValue('')
    expect(added).toHaveFocus()
    await userEvent.keyboard('RabbitMQ')
    expect(added).toHaveFocus()
    expect(added).toHaveValue('RabbitMQ')
    // Leaving the list regroups it.
    await userEvent.click(screen.getByLabelText('City'))
    expect(names('Messaging')).toEqual(['Kafka', 'RabbitMQ'])

    await save()
    await screen.findByText('Saved (version 2).')
    expect(puts[0]!.skills).toEqual([
      skill('Kafka'),
      skill('Kotlin', 'EXPERT'),
      skill('PostgreSQL', 'ADVANCED'),
      skill('RabbitMQ'),
    ])
  })

  it('shows an added skill even when the groups are closed or a filter is set', async () => {
    stubApi({ ...emptyProfile(), skills: many() })
    await mount()
    await userEvent.type(skills().getByLabelText('Filter skills'), 'kot')
    await userEvent.click(skills().getByRole('button', { name: 'Add skill' }))

    expect(skills().getByLabelText('Filter skills')).toHaveValue('')
    const added = document.querySelector(
      '[data-path="skills[21].name"]',
    ) as HTMLInputElement
    expect(added).toHaveFocus()
    await waitFor(() => expect(details('Other').open).toBe(true))
    expect(added).toBeVisible()
  })

  it('opens a closed group and focuses the skill with a message after a failed save', async () => {
    stubApi({ ...emptyProfile(), skills: many() }, () =>
      json(400, {
        error: 'validation_failed',
        fields: { 'skills[2].name': 'must not be blank' },
      }),
    )
    await mount()
    expect(details('Databases').open).toBe(false)
    await save()

    await screen.findByText('Skill name: must not be blank')
    expect(details('Databases').open).toBe(true)
    expect(document.querySelector('[data-path="skills[2].name"]')).toHaveFocus()
  })
})
