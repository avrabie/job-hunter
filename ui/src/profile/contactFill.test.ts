import { describe, expect, it } from 'vitest'
import {
  applyContactFill,
  contactConflicts,
  countChanged,
  hasContactValue,
} from './contactFill.ts'
import { emptyProfile, type Contact } from './types.ts'

const blankContact = (): Contact => emptyProfile().contact

const cv: Contact = {
  fullName: 'Ada Example',
  headline: 'Engineer',
  email: 'ada@example.com',
  phone: null,
  website: 'https://example.com',
  linkedin: null,
}

describe('contactFill', () => {
  it('fills only the empty fields', () => {
    const next = applyContactFill(
      { ...blankContact(), phone: '+41 79 000 00 00' },
      cv,
      false,
    )
    expect(next).toEqual({ ...cv, phone: '+41 79 000 00 00' })
  })

  it('treats a whitespace-only field as empty', () => {
    const next = applyContactFill(
      { ...blankContact(), fullName: '  ' },
      cv,
      false,
    )
    expect(next.fullName).toBe('Ada Example')
  })

  it('reports only fields with a different non-empty value as conflicts', () => {
    const current: Contact = {
      ...blankContact(),
      fullName: 'Grace Sample',
      email: 'ada@example.com',
      headline: 'Engineer',
    }
    expect(contactConflicts(current, cv)).toEqual(['Full name'])
  })

  it('replaces conflicts only when asked', () => {
    const current: Contact = { ...blankContact(), fullName: 'Grace Sample' }
    expect(applyContactFill(current, cv, false).fullName).toBe('Grace Sample')
    expect(applyContactFill(current, cv, true).fullName).toBe('Ada Example')
  })

  it('ignores empty CV values', () => {
    const current: Contact = {
      ...blankContact(),
      website: 'https://kept.example',
    }
    expect(applyContactFill(current, blankContact(), true)).toEqual(current)
  })

  it('counts the changed fields', () => {
    const next = applyContactFill(blankContact(), cv, false)
    expect(countChanged(blankContact(), next)).toBe(4)
  })

  it('knows whether the CV gave any value', () => {
    expect(hasContactValue(blankContact())).toBe(false)
    expect(hasContactValue({ ...blankContact(), email: 'x@example.com' })).toBe(
      true,
    )
  })
})
