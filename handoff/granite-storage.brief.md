# Brief: granite-storage (research agent, read-only)

**Goal:** find out how the project at `/Users/moldo/code/adrian/granite-security` stores user data and uploaded documents, so
Job Hunter can store a user's CV and extracted profile the same way where that makes sense. Job Hunter is a Kotlin / Spring
Boot / Postgres backend (Liquibase, JDBC, one JSON profile document per user in the `profile` module, every query scoped by
the token's `sub`).

**Read:** only `/Users/moldo/code/adrian/granite-security` (read-only, do not edit, do not run its build, do not copy secrets,
keys, real data or personal data into the report). In Job Hunter read only `backend/src/main/kotlin/net/s4v3/backend/profile/`
and `backend/src/main/resources/db/changelog/` to compare.

**Find and compare:**
1. Where and how files/documents are stored (database blobs, object storage, filesystem), size limits, content-type checks.
2. How per-user data is modelled and isolated; how it is encrypted, if at all (at rest, per-field, key handling).
3. Retention and deletion (hard delete of a user's data), export, audit.
4. Upload handling and virus/format validation, if any.
5. What is worth reusing for CV upload plus extracted profile data in Job Hunter, and what to avoid.

**May edit:** only `handoff/granite-storage.report.md`.

**Report:** under 120 lines: a short answer first, then findings with file paths in granite-security for each claim, then a
recommendation for Job Hunter (storage choice, schema sketch, deletion, encryption), then open questions for the user.
Never include secrets or personal data.
