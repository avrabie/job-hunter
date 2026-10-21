import type { User, UserManager } from 'oidc-client-ts'
import { vi } from 'vitest'

type Handler = (...args: never[]) => void

/** The small part of UserManager the UI uses, with spies and a way to fire its events. */
export function fakeManager(initialUser: User | null = null) {
  const loaded = new Set<Handler>()
  const renewErrors = new Set<Handler>()
  let current = initialUser
  const manager = {
    getUser: vi.fn(async () => current),
    removeUser: vi.fn(async () => {
      current = null
    }),
    signinRedirect: vi.fn(async () => {}),
    signoutRedirect: vi.fn(async () => {}),
    signinCallback: vi.fn(async (): Promise<User | void> => undefined),
    signinSilent: vi.fn(async (): Promise<User | null> => null),
    events: {
      addUserLoaded: (h: Handler) => loaded.add(h),
      removeUserLoaded: (h: Handler) => loaded.delete(h),
      addSilentRenewError: (h: Handler) => renewErrors.add(h),
      removeSilentRenewError: (h: Handler) => renewErrors.delete(h),
    },
  }
  return {
    manager: manager as unknown as UserManager,
    spies: manager,
    fireRenewError: () => renewErrors.forEach((h) => (h as () => void)()),
  }
}

export function fakeUser(
  over: Partial<{ access: string; email: string; expired: boolean }> = {},
): User {
  return {
    access_token: over.access ?? 'access-1',
    refresh_token: 'refresh-1',
    id_token: 'id-1',
    expired: over.expired ?? false,
    profile: {
      sub: 'user-1',
      email: over.email ?? 'ada@example.ch',
      name: 'Ada Lovelace',
    },
  } as unknown as User
}
