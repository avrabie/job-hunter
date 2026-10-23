import { describe, expect, it } from 'vitest'
import { formatSize } from './format.ts'

describe('formatSize', () => {
  it.each([
    [0, '1 KB'],
    [20480, '20 KB'],
    [1024 * 1024 - 1, '1024 KB'],
    [1024 * 1024, '1.0 MB'],
    [1.5 * 1024 * 1024, '1.5 MB'],
    [1024 * 1024 * 1024, '1.00 GB'],
    [10 * 1024 * 1024 * 1024, '10.00 GB'],
  ])('formats %i bytes as %s', (bytes, text) => {
    expect(formatSize(bytes)).toBe(text)
  })
})
