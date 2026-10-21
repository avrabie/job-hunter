import { User } from 'oidc-client-ts'
import { describe, expect, it } from 'vitest'
import { oidcSettings } from './config.ts'

describe('oidcSettings', () => {
  it.each([
    ['https://s4v3.net', 'https://s4v3.net'],
    ['http://localhost:8080', 'http://localhost:8080'],
  ])('derives every address from the origin %s', (origin, base) => {
    const s = oidcSettings(origin)
    expect(s.authority).toBe(`${base}/auth`)
    expect(s.redirect_uri).toBe(`${base}/callback`)
    expect(s.post_logout_redirect_uri).toBe(`${base}/`)
    expect(s.client_id).toBe('spa')
    expect(s.response_type).toBe('code')
    expect(s.scope).toBe('openid profile email')
  })

  it('keeps users in memory, not in any web storage', async () => {
    const s = oidcSettings('https://s4v3.net')
    const user = new User({
      access_token: 'secret-access',
      token_type: 'Bearer',
      profile: {
        sub: 'u1',
        iss: 'x',
        aud: 'spa',
        exp: 1,
        iat: 1,
        email: 'a@b.ch',
      },
    })
    await s.userStore!.set('user', user.toStorageString())

    expect(await s.userStore!.get('user')).toContain('secret-access')
    expect(JSON.stringify({ ...localStorage })).not.toContain('secret-access')
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('secret-access')
  })

  it('keeps the sign-in state in the given storage', async () => {
    const backing = new Map<string, string>()
    const storage = {
      get length() {
        return backing.size
      },
      key: (i: number) => [...backing.keys()][i] ?? null,
      getItem: (k: string) => backing.get(k) ?? null,
      setItem: (k: string, v: string) => void backing.set(k, v),
      removeItem: (k: string) => void backing.delete(k),
      clear: () => backing.clear(),
    } as Storage
    const s = oidcSettings('https://s4v3.net', storage)
    await s.stateStore!.set('abc', 'state-value')
    expect(backing.size).toBe(1)
  })
})
