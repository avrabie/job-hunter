import { describe, expect, it } from 'vitest'
import cases from './paragraphs.cases.json'
import { paragraphs } from './paragraphs.ts'

describe('paragraphs', () => {
  it.each(cases)('$name', ({ text, paragraphs: expected }) => {
    expect(paragraphs(text)).toEqual(expected)
  })
})
