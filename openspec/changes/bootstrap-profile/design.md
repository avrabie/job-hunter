# Design

## Context

`backend` today has the resource-server setup, `CurrentUser` (the token's `sub`), provisioning of `app_user` rows on first sight, Liquibase with `001-baseline.sql`, and a test-only notes endpoint that shows the per-user pattern (queries scoped by owner, other users' records answer 404). The `profile` and `web` modules exist only as empty packages. The UI has the API client (`apiFetch`, Bearer token, one retry on 401), routing and a shell. `PLAN.md` says the profile is the single source of facts for matching and drafting.

## Decisions

### Storage: one JSON document per user
Table `profile(owner_sub text primary key references app_user(sub) on delete cascade, document jsonb not null, schema_version int not null, version bigint not null, updated_at timestamptz not null default now())`. The profile is always read and replaced as a whole, matching and drafting consume it as a whole, and its shape will change while those features are built. A JSON column keeps migrations cheap now. `schema_version` (starts at 1) lets later changes migrate old documents. Normalised tables can come later if a query needs to look inside; nothing needs that yet. Considered and rejected: tables per section (many migrations and joins for no query benefit today).

### API: whole document, optimistic version
`GET /api/profile` returns the document plus `version`; with no row it returns an empty profile with version 0. `PUT /api/profile` takes the whole document including the `version` read. The update is a single statement: `update ... set document=?, version=version+1 where owner_sub=? and version=?`; first save is an `insert ... on conflict do nothing` that counts as success only when a row was inserted. Zero rows affected means 409. This is race-safe without locks. No PATCH: partial updates make validation and conflicts harder and the document is small.

### Where code lives
`profile` (public): `ProfileService` (`get(sub)`, `replace(sub, profile)`), the `Profile` data classes and enums, `ProfileConflictException`, `ProfileInvalidException`. `profile.internal`: `JdbcProfileRepository` and the validator. `web.internal`: `ProfileController` maps `/api/profile`, takes the `sub` from `CurrentUser`, converts exceptions to the status codes of the spec. `web` depends on `profile` and `accounts`; `profile` depends on no other module (the controller passes the `sub`), so there is no cycle. The service takes the `sub` as a parameter rather than reading `CurrentUser` so that later internal callers (matching, a scheduled job) can use it.

### Validation
A hand-written `ProfileValidator` in `profile.internal` checks every rule and builds the `fields` map directly, so the paths (`experience[0].title`) and messages are fully under our control and no rejected value can leak into a message. Bean Validation was dropped for this: it would need annotations on every nested class and still needs hand-written cross-field checks. `ProfileService.parse` uses its own strict Jackson mapper (unknown properties and trailing content fail; unknown enum values fail by default). A parse failure reports only the property path of the exception, never its message, because Jackson quotes the input there. The API body is an envelope `{"version": n, "profile": {...}}`; error paths are relative to `profile`. A `@RestControllerAdvice` limited to the profile controller turns `ProfileInvalidException` into 400 and `ProfileConflictException` into 409. Months are a small value class parsed from `YYYY-MM`. The body limit is a filter on `/api/profile` that checks `Content-Length` and counts bytes while reading, answering 413 before parsing; the gateway and nginx limits stay as they are.

### No content in logs
No `log.*` call receives a profile or any field of it. Exception messages carry only the rule and the path. A test captures the log output over a save, a 400 and a 409 and asserts that a marker string sent in every text field never appears. Spring's request logging stays off (it is off today).

### UI
`ui/src/profile/`: `types.ts` (hand-written types that mirror the API; replaced by a generated client once an OpenAPI document exists), `profileApi.ts` (uses `apiFetch`, maps 400 `fields`, 409), `ProfilePage.tsx` with small section components, reducer-based form state so list edits are simple. Plain CSS, no form library. Unsaved-edits guard with a `beforeunload` handler while the form is dirty, removed after save. The route is wrapped so an unauthenticated visitor starts the existing sign-in and returns to `/profile` (the callback already navigates to `/`; the target is kept as a path in `sessionStorage` under the existing sign-in state, never the profile itself).

### Deploy
`backend` image `0.2.0` and `ui` image `0.2.0` (amd64) pushed to Docker Hub, prod overlay tags updated, applied to `s4v3` with explicit `--context`. The `ui` workload needs no manifest change; `backend` migrates the schema on start.

## Risks and trade-offs

- **Frameworks print values below INFO.** The log test found that Spring MVC logs the response object at DEBUG (`org.springframework.web`), and Spring JDBC (`org.springframework.jdbc.core`) and the PostgreSQL driver (`org.postgresql`) log every bound SQL parameter at TRACE, which includes the whole profile. The application runs at INFO, so none of this is on; the test captures everything else at TRACE and leaves these three out. Rule, written into `backend/README.md`: never enable those loggers below INFO outside a throwaway local run.

- A JSON document is not queryable in SQL as cheaply as columns. Accepted: nothing queries inside it yet, and `jsonb` still allows indexes later.
- Whole-document replace means the last form save wins only if versions match; two tabs by the same user get a visible conflict instead of silent loss.
- The hand-written TypeScript types can drift from the Kotlin classes. Mitigation: one backend test serialises a full profile and a UI test parses the same fixture (kept identical in both places) until a generated client replaces it.
- Limits (30 experience entries, 4000-character summary and so on) are first guesses; they are constants in one place and easy to raise.

## Migration plan

Liquibase changeset `004-profile` adds the table; rollback drops it. Deploy order: `backend` first (the new endpoint is additive), then `ui`. Roll back by re-applying the previous image tags; the table can stay.

## Open questions

- Whether `workAuthorisation` needs a "valid until" date; left out until matching proves it matters.
- Whether the profile should carry a free-text "extra facts" field for things that fit no section; deferred to the CV-import change, which will show what is missing.
