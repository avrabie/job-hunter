import {
  InMemoryWebStorage,
  UserManager,
  WebStorageStateStore,
  type UserManagerSettings,
} from 'oidc-client-ts'

export const CLIENT_ID = 'spa'

/**
 * Settings derived from the origin the page was loaded from, so the same build works at the public hostname and behind
 * the dev proxy. Tokens live in memory only; the sign-in state (random state and PKCE verifier) must survive the
 * redirect to the auth service, so it uses sessionStorage. It holds no token and no personal data.
 */
export function oidcSettings(
  origin: string,
  signInStateStorage: Storage = window.sessionStorage,
): UserManagerSettings {
  return {
    authority: `${origin}/auth`,
    client_id: CLIENT_ID,
    redirect_uri: `${origin}/callback`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code',
    scope: 'openid profile email',
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    stateStore: new WebStorageStateStore({ store: signInStateStorage }),
    // Renews with the refresh token before the access token expires; no iframe is involved.
    automaticSilentRenew: true,
    // Identity comes from the ID token; no extra call to the userinfo endpoint.
    loadUserInfo: false,
  }
}

export function createUserManager(
  origin: string = window.location.origin,
): UserManager {
  return new UserManager(oidcSettings(origin))
}
