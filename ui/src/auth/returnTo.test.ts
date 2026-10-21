import { describe, expect, it } from 'vitest'
import { safeReturnTo } from './returnTo.ts'

describe('safeReturnTo', () => {
  it('keeps our own pages', () => {
    expect(safeReturnTo('/profile')).toBe('/profile')
    expect(safeReturnTo('/')).toBe('/')
  })

  it('sends everything else home, so no outside address is ever followed', () => {
    for (const bad of [
      'https://evil.example/',
      '//evil.example',
      '/\\evil.example',
      '/profile/../x',
      '/callback',
      'profile',
      '',
      undefined,
      null,
      42,
      { returnTo: '/profile' },
    ]) {
      expect(safeReturnTo(bad)).toBe('/')
    }
  })
})
