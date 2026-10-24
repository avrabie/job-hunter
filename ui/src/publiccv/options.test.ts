import { describe, expect, it } from 'vitest'
import {
  HANDLE_FORM_MESSAGE,
  PUBLIC_CV_PATH,
  defaultSettings,
  handleProblem,
  moveSection,
  setSection,
} from './options.ts'

describe('defaultSettings', () => {
  it('starts with everything off, no sections, Classic and English', () => {
    expect(defaultSettings()).toEqual({
      template: 'CLASSIC',
      language: 'EN',
      sections: [],
      headline: false,
      analysis: false,
      workAuthorisation: false,
      location: false,
      contact: {
        email: false,
        phone: false,
        website: false,
        linkedin: false,
        codeAccounts: false,
      },
      indexable: false,
    })
  })
})

describe('PUBLIC_CV_PATH', () => {
  it('is the route the address is built from', () => {
    expect(PUBLIC_CV_PATH).toBe('/cv/')
  })
})

describe('handleProblem', () => {
  it.each(['ada-example', 'abc', 'a1b2c3', 'x'.repeat(30)])(
    'accepts %s',
    (handle) => {
      expect(handleProblem(handle)).toBeNull()
    },
  )

  it.each([
    ['too short', 'Ad'],
    ['too long', 'x'.repeat(31)],
    ['upper case', 'Ada-Example'],
    ['space', 'ada example'],
    ['leading hyphen', '-ada'],
    ['double hyphen', 'ada--example'],
    ['trailing hyphen', 'ada-'],
    ['underscore', 'ada_example'],
    ['empty', ''],
  ])('rejects a handle that is %s', (_name, handle) => {
    expect(handleProblem(handle)).toBe(HANDLE_FORM_MESSAGE)
  })

  it('says the length and the characters in the message', () => {
    expect(HANDLE_FORM_MESSAGE).toContain('3 to 30 characters')
  })
})

describe('setSection', () => {
  it('adds a ticked section at the end and keeps the order of the others', () => {
    expect(setSection(['SKILLS'], 'EXPERIENCE', true)).toEqual([
      'SKILLS',
      'EXPERIENCE',
    ])
  })

  it('removes an unticked section and keeps the rest in order', () => {
    expect(setSection(['SKILLS', 'EXPERIENCE'], 'SKILLS', false)).toEqual([
      'EXPERIENCE',
    ])
  })

  it('ticking a section twice does not repeat it', () => {
    expect(setSection(['SKILLS'], 'SKILLS', true)).toEqual(['SKILLS'])
  })
})

describe('moveSection', () => {
  const list = ['PROFILE', 'EXPERIENCE', 'SKILLS'] as const

  it('swaps a section with its neighbour', () => {
    expect(moveSection([...list], 2, -1)).toEqual([
      'PROFILE',
      'SKILLS',
      'EXPERIENCE',
    ])
  })

  it('changes nothing when moved past either end', () => {
    expect(moveSection([...list], 0, -1)).toEqual([...list])
    expect(moveSection([...list], 2, 1)).toEqual([...list])
  })

  it('does not change the list it was given', () => {
    const given = [...list]
    moveSection(given, 0, 1)
    expect(given).toEqual([...list])
  })
})
