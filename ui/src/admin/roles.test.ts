import { describe, expect, it } from 'vitest'
import { hasRole } from './roles.ts'

const token = (claims: unknown) =>
  `h.${btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')}.s`

describe('hasRole', () => {
  it('is true when the roles claim lists the role', () => {
    expect(hasRole(token({ roles: ['USER', 'ADMIN'] }), 'ADMIN')).toBe(true)
  })
  it('is false without the role', () => {
    expect(hasRole(token({ roles: ['USER'] }), 'ADMIN')).toBe(false)
    expect(hasRole(token({}), 'ADMIN')).toBe(false)
    expect(hasRole(token({ roles: 'ADMIN' }), 'ADMIN')).toBe(false)
  })
  it('is false for a missing or unreadable token', () => {
    expect(hasRole(null, 'ADMIN')).toBe(false)
    expect(hasRole('garbage', 'ADMIN')).toBe(false)
    expect(hasRole('a.!!!.c', 'ADMIN')).toBe(false)
  })
})
