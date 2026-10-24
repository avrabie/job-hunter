# cx-contact report: export-profile-cv, tasks 1.1-1.3

Worktree: `worktree-agent-a89d3459362049af9`. Nothing committed.

## Tasks finished

- **1.1** `Profile.kt`: `data class Contact` (six optional strings, all null by default) and `contact: Contact = Contact()` as the first field of `Profile`.
  Verify: `./gradlew compileKotlin` and `compileTestKotlin` succeed (BUILD SUCCESSFUL).
- **1.2** `ProfileValidator.kt`: `contact(...)` check, called first in `check()`.
  - `fullName`, `headline`: optional, max 200, not blank when given.
  - `email`: max 200 and shape `[^@\s]+@[^@\s]+\.[^@\s]+`.
  - `phone`: max 30, characters `0-9 +()/-` only.
  - `website`, `linkedin`: absolute https, max 500, via the existing `httpUrl`.
  - Messages are constant strings; no value is echoed.
  - `ProfileValidatorTests`: 10 new cases (valid full, empty, bad email x2, phone letters, phone 30 pass / 31 fail, http and javascript website, http linkedin, fullName 201, no-echo across all six fields).
  - Verify: `./gradlew test --tests "*ProfileValidator*"`: 61 tests, 0 failures, 0 errors.
- **1.3** Fixture and tests:
  - `full-profile.json` has the Contract's contact block as its first field.
  - `ProfileServiceTests`: new legacy test "a profile stored before contact existed reads with an all-null contact" (schema row version 8).
  - New `ProfileContactLoggingTests.kt`, copied from `ProfileLoggingTests`: a marker email is saved (200) and an invalid one is rejected (400); the captured log must not contain the marker.
  - The round-trip test in `ProfileServiceTests` (`a profile using every field round-trips`) covers the fixture contact.
  - Verify: `./gradlew test --tests "*Profile*"`: BUILD SUCCESSFUL. Per suite: ProfileServiceTests 17, ProfileApiTests 14, ProfileLoggingTests 1, ProfileContactLoggingTests 1, ProfileSchemaTests 1, ProfileSavedEventTests 4, ProfileValidatorTests 61. Total 99, 0 failures, 0 errors.

## Tasks not finished

None.

## Files created or changed

- `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/ProfileValidator.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/ProfileValidatorTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/ProfileServiceTests.kt`
- `backend/src/test/resources/profile/full-profile.json`
- `backend/src/test/kotlin/net/s4v3/backend/ProfileContactLoggingTests.kt` (new)
- `openspec/changes/export-profile-cv/tasks.md` (ticked 1.1, 1.2, 1.3 only)
- `handoff/cx-contact.report.md` (this file)

## Surprises and decisions for the lead

- **Logging test name.** The task says to copy the profile logging test. I named the class `ProfileContactLoggingTests` (not `ContactLoggingTests`) so that the verify filter `*Profile*` actually runs it. The first run without the "Profile" prefix showed it was skipped by the filter.
- **Phone rule is characters only.** As the spec says, a value such as `()` or `+` passes; the spec sets no minimum digit count. Say if you want at least one digit.
- **Email rule is a shape check.** `a@b.c` passes. It is not a full RFC check, as the spec asks.
- **Blank text.** Like `location.city`, a blank `contact.*` text is rejected ("must not be blank when given"). The UI must send empty text as null, which task 5.1 already says.
- **Shell note.** A `./gradlew test --tests '*X*'` call was refused by the worktree guard because of the quoted wildcard. Running it with double quotes in a plain command worked.
