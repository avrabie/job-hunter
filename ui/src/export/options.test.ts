import { describe, expect, it } from 'vitest'
import {
  SECTION_KINDS,
  defaultOptions,
  moveSection,
  toRequest,
  type SectionChoice,
} from './options.ts'

describe('defaultOptions', () => {
  it('starts as PDF, Classic, English, every section ticked in the default order', () => {
    const options = defaultOptions()
    expect(options.format).toBe('PDF')
    expect(options.template).toBe('CLASSIC')
    expect(options.language).toBe('EN')
    expect(options.sections.map((s) => s.kind)).toEqual([...SECTION_KINDS])
    expect(options.sections.every((s) => s.included)).toBe(true)
    expect(options.includeOpenSource).toBe(true)
    expect(options.includeAnalysis).toBe(false)
    expect(options.includeWorkAuthorisation).toBe(true)
  })
})

describe('moveSection', () => {
  const list: SectionChoice[] = SECTION_KINDS.map((kind) => ({
    kind,
    included: true,
  }))

  it('swaps a section with its neighbour', () => {
    const moved = moveSection(list, 1, -1)
    expect(moved[0]!.kind).toBe('EXPERIENCE')
    expect(moved[1]!.kind).toBe('PROFILE')
  })

  it('changes nothing when moved past either end', () => {
    expect(moveSection(list, 0, -1)).toBe(list)
    expect(moveSection(list, list.length - 1, 1)).toBe(list)
  })

  it('does not change the list it was given', () => {
    moveSection(list, 0, 1)
    expect(list[0]!.kind).toBe('PROFILE')
  })
})

describe('toRequest', () => {
  it('sends the ticked sections in the order shown', () => {
    const options = defaultOptions()
    options.sections = [
      { kind: 'SKILLS', included: true },
      { kind: 'EXPERIENCE', included: true },
      { kind: 'PUBLICATIONS', included: false },
    ]
    expect(toRequest(options).sections).toEqual(['SKILLS', 'EXPERIENCE'])
  })

  it('sends the options as the backend names them', () => {
    const options = {
      ...defaultOptions(),
      format: 'HTML' as const,
      template: 'MODERN' as const,
      language: 'DE' as const,
    }
    expect(toRequest(options)).toEqual({
      format: 'HTML',
      template: 'MODERN',
      language: 'DE',
      sections: [...SECTION_KINDS],
      includeOpenSource: true,
      includeAnalysis: false,
      includeWorkAuthorisation: true,
    })
  })

  it('sends no analysis summaries when open source projects are off', () => {
    const options = {
      ...defaultOptions(),
      includeOpenSource: false,
      includeAnalysis: true,
    }
    expect(toRequest(options).includeAnalysis).toBe(false)
  })

  it('keeps the analysis choice for when open source projects come back on', () => {
    const options = { ...defaultOptions(), includeAnalysis: true }
    expect(toRequest(options).includeAnalysis).toBe(true)
  })
})
