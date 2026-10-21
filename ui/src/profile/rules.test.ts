import { describe, expect, it } from 'vitest'
import fixture from './fixtures/full-profile.json'
import { checkProfile } from './rules.ts'
import {
  blankEducation,
  blankExperience,
  blankLanguage,
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

  it('checks preferred cantons like the location canton', () => {
    expect(checkProfile(withPrefs({ cantons: ['ZH', 'be'] }))).toEqual({
      'preferences.cantons[1]': 'must be two uppercase letters',
    })
  })
})
