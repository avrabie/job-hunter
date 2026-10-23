# pub-backend report (profile-publications, tasks 1.1, 1.2, 1.3, 2.1)

## Tasks finished

- 1.1 `./gradlew compileKotlin`: BUILD SUCCESSFUL.
- 1.2 `./gradlew test --tests '*ProfileValidator*'`: 12 tests, 0 failures. Combined `./gradlew test --tests '*Profile*'`: all Profile suites green (ProfileServiceTests 11, ProfileApiTests 11, ProfileValidatorTests 12, ProfileSavedEventTests 4, ProfileSchemaTests 1, ProfileLoggingTests 1), 0 failures.
- 1.3 Covered by the same `*Profile*` run: round-trip test (`a profile using every field round-trips`) now includes the publication; new test `a profile stored before publications existed reads with an empty list` (raw insert via JdbcTemplate, no `publications` key).
- 2.1 `./gradlew test --tests '*DraftMapper*' --tests '*KoogCvReader*'`: DraftMapperTests 9 (3 new), KoogCvReaderTests 7, 0 failures.

## Tasks not finished

- None of 3.x or 4.x (out of scope). Nothing committed.

## Files changed

- backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt
- backend/src/main/kotlin/net/s4v3/backend/profile/internal/ProfileValidator.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/CvProfileDraft.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/DraftMapper.kt
- backend/src/test/kotlin/net/s4v3/backend/profile/internal/ProfileValidatorTests.kt (new)
- backend/src/test/kotlin/net/s4v3/backend/ProfileServiceTests.kt
- backend/src/test/kotlin/net/s4v3/backend/llm/DraftMapperTests.kt
- backend/src/test/resources/profile/full-profile.json
- openspec/changes/profile-publications/tasks.md (ticked 1.1, 1.2, 1.3, 2.1)
- handoff/pub-backend.report.md (this file)

## Surprises and decisions

- Task 1.2 asked for `ProfileValidatorTests` "or the existing class". No such class existed (`grep ProfileValidator backend/src/test` found nothing), so I created it in package `net.s4v3.backend.profile.internal` (same pattern as `DraftMapperTests`, plain unit test, no Testcontainers). Tell me if you want it moved next to ProfileServiceTests.
- The validator's year upper bound and the draft mapper's year bound both use `LocalDate.now().year + 1`.
- Title and venue use `NAME_MAX` (200), authors `AUTHORS_MAX` (500), url `URL_MAX` (500). The url check accepts only absolute URIs with scheme http or https and a non-empty host (`java.net.URI`).
- The draft mapper does not enforce length limits (the spec does not ask for it); an over-long venue from the model will fail on Save, as other draft fields do.
- No `ProfileValidator` messages contain the submitted value; a test checks this.
