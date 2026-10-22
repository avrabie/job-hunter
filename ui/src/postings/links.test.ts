import { describe, expect, it } from 'vitest'
import { safeHttpUrl } from './links.ts'

describe('safeHttpUrl', () => {
  it('accepts http and https', () => {
    expect(safeHttpUrl('https://example.org/jobs?id=1')).toBe(
      'https://example.org/jobs?id=1',
    )
    expect(safeHttpUrl('http://example.org/')).toBe('http://example.org/')
  })

  it('refuses other schemes and junk', () => {
    for (const bad of [
      'javascript:alert(1)',
      'JavaScript:alert(1)',
      'data:text/html,<b>x</b>',
      'ftp://example.org/',
      'file:///etc/passwd',
      '//example.org/x',
      'not a url',
      '',
    ]) {
      expect(safeHttpUrl(bad), bad).toBeNull()
    }
  })
})
