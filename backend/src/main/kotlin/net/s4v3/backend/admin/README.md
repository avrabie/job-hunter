# admin

Purpose: what an administrator sees about the users: the account, the profile's contact fields, and each user's tracked jobs by stage (saved, applying, applied, rejected). Read-only.

## Public API (this package)
- None. The endpoints are the API: `GET /api/admin/users` and `GET /api/admin/users/{sub}`.

## Internal (`internal/`)
- `AdminUsersController`: both endpoints. `/api/admin/**` needs `ROLE_ADMIN` by the security chain.
- `AdminUserRepository`: read-only SQL over `app_user`, `profile` (contact and location fields only) and `application`.
- `AdminUserModel`: response shapes. Application descriptions and notes are never selected.

## Events
- None.

## Tables
- Reads `app_user`, `profile`, `application`. Writes nothing.

## Calls
- None outside the database.
