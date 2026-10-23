import { describe, expect, it } from 'vitest'
import fixture from './fixtures/full-profile.json'
import { checkProfile } from './rules.ts'
import {
  blankEducation,
  blankPublication,
  blankExperience,
  blankLanguage,
  blankProject,
  blankSkill,
  emptyProfile,
  type Profile,
} from './types.ts'

const full = fixture as unknown as Profile
const withPrefs = (patch: Partial<Profile['preferences']>): Profile => ({
  ...emptyProfile(),
  preferences: { ...emptyProfile().preferences, ...patch },
})

// The expected messages are the ones `ProfileValidator` in the backend produces; a change there needs one here.
describe('checkProfile', () => {
  it('finds nothing wrong with the full sample or an empty profile', () => {
    expect(checkProfile(full)).toEqual({})
    expect(checkProfile(emptyProfile())).toEqual({})
  })

  it.each([
    ['ZH', undefined],
    ['zh', 'must be two uppercase letters'],
    ['ZUR', 'must be two uppercase letters'],
    ['Z', 'must be two uppercase letters'],
  ])('canton %s', (canton, message) => {
    const p = { ...emptyProfile(), location: { city: null, canton } }
    expect(checkProfile(p)['location.canton']).toBe(message)
  })

  it.each([
    ['de', undefined],
    ['DE', 'must be two lowercase letters'],
    ['deu', 'must be two lowercase letters'],
    ['', 'must be two lowercase letters'],
  ])('language code "%s"', (code, message) => {
    const p = {
      ...emptyProfile(),
      languages: [{ ...blankLanguage(), code }],
    }
    expect(checkProfile(p)['languages[0].code']).toBe(message)
  })

  it('requires text in entries, ignoring surrounding spaces', () => {
    const p: Profile = {
      ...emptyProfile(),
      experience: [
        {
          ...blankExperience(),
          employer: '  ',
          title: 'Dev',
          achievements: [' '],
        },
      ],
      education: [blankEducation()],
      skills: [blankSkill()],
      preferences: { ...emptyProfile().preferences, targetRoles: [''] },
    }
    expect(checkProfile(p)).toEqual({
      'experience[0].employer': 'must not be blank',
      'experience[0].achievements[0]': 'must not be blank',
      'education[0].institution': 'must not be blank',
      'education[0].degree': 'must not be blank',
      'skills[0].name': 'must not be blank',
      'preferences.targetRoles[0]': 'must not be blank',
    })
  })

  it('flags an end month before the start, not an equal or missing one', () => {
    const entry = (start: string, end: string | null) => ({
      ...blankExperience(),
      employer: 'A',
      title: 'B',
      start,
      end,
    })
    const check = (start: string, end: string | null) =>
      checkProfile({ ...emptyProfile(), experience: [entry(start, end)] })
    expect(check('2020-05', '2020-04')['experience[0].end']).toBe(
      'must not be before the start',
    )
    expect(check('2020-05', '2020-05')).toEqual({})
    expect(check('2020-05', null)).toEqual({})
    const edu = {
      ...emptyProfile(),
      education: [
        {
          ...blankEducation(),
          institution: 'U',
          degree: 'D',
          start: '2019-09',
          end: '2018-01',
        },
      ],
    }
    expect(checkProfile(edu)['education[0].end']).toBe(
      'must not be before the start',
    )
  })

  it.each([
    [9, 'must be between 10 and 100'],
    [10, undefined],
    [100, undefined],
    [101, 'must be between 10 and 100'],
  ])('workload %i', (n, message) => {
    expect(
      checkProfile(withPrefs({ workloadMin: n }))['preferences.workloadMin'],
    ).toBe(message)
    expect(
      checkProfile(withPrefs({ workloadMax: n }))['preferences.workloadMax'],
    ).toBe(message)
  })

  it('flags a minimum workload above the maximum', () => {
    expect(
      checkProfile(withPrefs({ workloadMin: 80, workloadMax: 60 })),
    ).toEqual({ 'preferences.workloadMax': 'must not be below the minimum' })
    expect(
      checkProfile(withPrefs({ workloadMin: 60, workloadMax: 60 })),
    ).toEqual({})
  })

  it('flags a negative salary but accepts zero', () => {
    expect(checkProfile(withPrefs({ minSalaryChf: -1 }))).toEqual({
      'preferences.minSalaryChf': 'must not be negative',
    })
    expect(checkProfile(withPrefs({ minSalaryChf: 0 }))).toEqual({})
  })

  it('checks a publication title, year and link', () => {
    const year = new Date().getFullYear()
    const pub = (patch: Partial<Profile['publications'][number]>) =>
      checkProfile({
        ...emptyProfile(),
        publications: [{ ...blankPublication(), title: 'P', ...patch }],
      })
    expect(pub({ title: ' ' })).toEqual({
      'publications[0].title': 'must not be blank',
    })
    expect(pub({ year: 1850 })).toEqual({
      'publications[0].year': `must be between 1900 and ${year + 1}`,
    })
    expect(pub({ year: year + 2 })).toEqual({
      'publications[0].year': `must be between 1900 and ${year + 1}`,
    })
    expect(pub({ year: year + 1 })).toEqual({})
    expect(pub({ url: 'javascript:alert(1)' })).toEqual({
      'publications[0].url': 'must be an http or https address',
    })
    expect(pub({ url: '/relative/path' })).toEqual({
      'publications[0].url': 'must be an http or https address',
    })
    expect(pub({ url: 'http://example.org/p' })).toEqual({})
    expect(pub({ title: 'x'.repeat(201) })).toEqual({
      'publications[0].title': 'must be at most 200 characters',
    })
    expect(pub({ authors: 'a'.repeat(501) })).toEqual({
      'publications[0].authors': 'must be at most 500 characters',
    })
    expect(pub({ venue: 'v'.repeat(201) })).toEqual({
      'publications[0].venue': 'must be at most 200 characters',
    })
  })

  it('checks a project name, its dates and its links', () => {
    const project = (patch: Partial<Profile['projects'][number]>) =>
      checkProfile({
        ...emptyProfile(),
        projects: [{ ...blankProject(), name: 'Tool', ...patch }],
      })
    expect(project({ name: ' ' })).toEqual({
      'projects[0].name': 'must not be blank',
    })
    expect(project({ start: '2023-06', end: '2023-02' })).toEqual({
      'projects[0].end': 'must not be before the start',
    })
    expect(project({ start: '2023-02', end: '2023-02' })).toEqual({})
    expect(project({ start: '2023-02', end: null })).toEqual({})
    expect(
      project({ links: [{ label: 'Repo', url: 'javascript:alert(1)' }] }),
    ).toEqual({
      'projects[0].links[0].url': 'must be an http or https address',
    })
    expect(project({ links: [{ label: 'Repo', url: '' }] })).toEqual({
      'projects[0].links[0].url': 'must be an http or https address',
    })
    expect(
      project({ links: [{ label: ' ', url: 'https://example.org/x' }] }),
    ).toEqual({ 'projects[0].links[0].label': 'must not be blank' })
    expect(
      project({
        links: [
          { label: 'Code', url: 'https://example.org/x' },
          { label: '', url: '' },
        ],
      }),
    ).toEqual({})
  })

  it('reports a bad link at the index the user sees, after a blank row', () => {
    const p = checkProfile({
      ...emptyProfile(),
      projects: [
        {
          ...blankProject(),
          name: 'Tool',
          links: [
            { label: '', url: '' },
            { label: 'Site', url: 'ftp://example.org' },
          ],
        },
      ],
    })
    expect(p).toEqual({
      'projects[0].links[1].url': 'must be an http or https address',
    })
  })

  it('checks preferred cantons like the location canton', () => {
    expect(checkProfile(withPrefs({ cantons: ['ZH', 'be'] }))).toEqual({
      'preferences.cantons[1]': 'must be two uppercase letters',
    })
  })
})
