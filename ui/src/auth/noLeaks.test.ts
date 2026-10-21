import { UserManager } from 'oidc-client-ts'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { oidcSettings } from './config.ts'

const ORIGIN = 'https://s4v3.net'
const ISSUER = `${ORIGIN}/auth`
const EMAIL = 'ada.lovelace@example.ch'
const NAME = 'Ada Lovelace'
const SECRETS = [
  'at-secret-1',
  'rt-secret-1',
  'at-secret-2',
  'rt-secret-2',
  EMAIL,
  NAME,
]

const b64 = (o: object) =>
  btoa(JSON.stringify(o))
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
const jwt = (payload: object) =>
  `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64(payload)}.sig`
const json = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

let tokenCalls = 0
let nonce = ''

function stubServer() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/.well-known/openid-configuration')) {
        return json({
          issuer: ISSUER,
          authorization_endpoint: `${ISSUER}/oauth2/authorize`,
          token_endpoint: `${ISSUER}/oauth2/token`,
          end_session_endpoint: `${ISSUER}/connect/logout`,
          jwks_uri: `${ISSUER}/oauth2/jwks`,
        })
      }
      if (url.endsWith('/oauth2/token')) {
        tokenCalls += 1
        const n = tokenCalls
        const now = Math.floor(Date.now() / 1000)
        return json({
          access_token: `at-secret-${n}`,
          refresh_token: `rt-secret-${n}`,
          token_type: 'Bearer',
          expires_in: 899,
          scope: 'openid profile email',
          id_token: jwt({
            iss: ISSUER,
            aud: 'spa',
            sub: 'user-1',
            exp: now + 900,
            iat: now,
            nonce,
            email: EMAIL,
            name: NAME,
          }),
        })
      }
      throw new Error(`unexpected request ${url}`)
    }),
  )
}

const consoleSpies = ['log', 'info', 'warn', 'error', 'debug'].map((m) =>
  vi.spyOn(console, m as 'log').mockImplementation(() => {}),
)

beforeEach(() => {
  tokenCalls = 0
  window.localStorage.clear()
  window.sessionStorage.clear()
  stubServer()
})

afterEach(() => {
  vi.unstubAllGlobals()
  consoleSpies.forEach((s) => s.mockClear())
})

function everythingVisibleToScripts(): string {
  return JSON.stringify({
    local: { ...window.localStorage },
    session: { ...window.sessionStorage },
    cookie: document.cookie,
    console: consoleSpies.map((s) => s.mock.calls),
  })
}

it('writes no token or personal value to storage, cookies or the console during sign-in, renewal and sign-out', async () => {
  const manager = new UserManager(oidcSettings(ORIGIN))

  // Start: the redirect to the auth service stores a random state and verifier, but no token and no identity.
  // jsdom cannot navigate, so the redirect itself is a no-op; the stored sign-in state is what the callback needs.
  // The promise of a redirect only settles when the page is left, so it is not awaited.
  void manager.signinRedirect()
  await vi.waitFor(() =>
    expect(
      Object.keys(window.sessionStorage).some((k) => k.startsWith('oidc.')),
    ).toBe(true),
  )
  const key = Object.keys(window.sessionStorage).find((k) =>
    k.startsWith('oidc.'),
  )!
  const state = key.slice('oidc.'.length)
  nonce =
    (JSON.parse(window.sessionStorage.getItem(key)!) as { nonce?: string })
      .nonce ?? ''
  expect(everythingVisibleToScripts()).not.toMatch(
    new RegExp(SECRETS.join('|')),
  )

  // Callback: the code is exchanged for tokens.
  const user = await manager.signinCallback(
    `${ORIGIN}/callback?code=abc&state=${state}`,
  )
  expect(user?.access_token).toBe('at-secret-1')
  expect(user?.profile.email).toBe(EMAIL)
  expect(everythingVisibleToScripts()).not.toMatch(
    new RegExp(SECRETS.join('|')),
  )

  // Renewal with the refresh token.
  const renewed = await manager.signinSilent()
  expect(renewed?.access_token).toBe('at-secret-2')
  expect(everythingVisibleToScripts()).not.toMatch(
    new RegExp(SECRETS.join('|')),
  )

  // The tokens are still available in memory.
  expect((await manager.getUser())?.access_token).toBe('at-secret-2')

  await manager.removeUser()
  expect(await manager.getUser()).toBeNull()
  expect(everythingVisibleToScripts()).not.toMatch(
    new RegExp(SECRETS.join('|')),
  )
})
