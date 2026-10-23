# Tasks

Rules for every task (see `handoff/haiku-rules.md`): edit only the files named, copy the pattern of the existing code named, run the verify command, tick the box, and stop and report if blocked.

## 1. Backend model and validation

- [x] 1.1 In `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt` add `data class Publication(val title: String, val venue: String? = null, val year: Int? = null, val authors: String? = null, val url: String? = null)` and `val publications: List<Publication> = emptyList()` on `Profile` (after `education`). Copy the style of `Education`. Verify: `cd backend && ./gradlew compileKotlin` succeeds.
- [x] 1.2 In `profile/internal/ProfileValidator.kt` add constants `PUBLICATIONS_MAX = 100`, `URL_MAX = 500`, `AUTHORS_MAX = 500` and the rules from the `profile` spec (blank title, text lengths, year 1900..currentYear+1, url must be an absolute http or https URI with a host, at most 100 entries), with paths like `publications[0].year`. Copy how `education` is checked and how `optionalText` is used; messages never contain the submitted value. Verify with new tests in `ProfileValidatorTests` (or the existing profile validation test class; find it with `grep -rl ProfileValidator backend/src/test`): one test per scenario in the `profile` spec (valid, title only, blank title, year 1850, year current+2, `javascript:alert(1)`, relative path, 101 entries), then `./gradlew test --tests '*Profile*'` passes.
- [x] 1.3 Add one publication to `backend/src/test/resources/profile/full-profile.json` exactly as given in the contract below, and make the round-trip test (`grep -rl full-profile backend/src/test`) pass; add a test that a stored document without `publications` reads with an empty list (insert a JSON document without the field through `JdbcTemplate`, then `ProfileService.get`). Verify: `./gradlew test --tests '*Profile*'`.

## 2. Backend CV draft

- [x] 2.1 In `llm/internal/CvProfileDraft.kt` add `PublicationDraft` (all strings, `title` required, others nullable, `@property:LLMDescription` on each, `year` described as "four-digit year or null") and `publications: List<PublicationDraft> = emptyList()` on `CvProfileDraft`. In `DraftMapper.kt` map it to `Publication`: blank title leaves the entry out, `year` parsed with `toIntOrNull()` and kept only within 1900..currentYear+1, `url` kept only when it starts with `http://` or `https://` (case-insensitive), all text trimmed with blanks as null. Verify: add the three `llm` spec scenarios to `DraftMapperTests` and `./gradlew test --tests '*DraftMapper*' --tests '*KoogCvReader*'` passes.

## 3. UI model and form

- [x] 3.1 In `ui/src/profile/types.ts` add `Publication` (`title: string; venue: string | null; year: number | null; authors: string | null; url: string | null`), `publications: Publication[]` on `Profile` (after `education`), `publications: []` in `emptyProfile()`, a `blankPublication()` like `blankEducation()`, and the trimming and null-for-empty handling in `toRequestProfile` (year stays a number or null). In `ui/src/profile/rules.ts` add the checks (title required, year 1900..current year+1, url `http`/`https` via `new URL`, lengths) using the same paths as the server. Add the same publication sample to `ui/src/profile/fixtures/full-profile.json`. Verify: add cases to `rules.test.ts` and `profile.test.ts`; `cd ui && npx vitest run src/profile` passes (the drift test compares with the backend fixture, so task 1.3 must be done first).
- [x] 3.2 In `ui/src/profile/ProfileForm.tsx` (and `fields.tsx` if a helper is needed) add a Publications section after Education, copying how the Education section is built (add, remove, move up and down, fields Title, Venue, Year, Authors, Link). Verify: add component tests to `ProfilePage.test.tsx` for the three `ui-profile` scenarios (add with title and year sends null venue/authors/link; bad link blocks save with a message; reorder and remove), then `cd ui && npx vitest run && npx tsc -b && npx oxlint --deny-warnings && npx prettier --check .` all pass.

## 4. Docs

- [ ] 4.1 Mention publications in `backend/README.md` (profile content), `ui/README.md` (profile page) and `PLAN.md` (profile row). Verify: `grep -n -i publication` shows each file.

## Contract (do not change)

The publication JSON sample for both fixtures:
`{"title":"Fast Joins on Skewed Data","venue":"VLDB","year":2021,"authors":"J. Doe, A. Smith","url":"https://example.org/papers/fast-joins"}`
Profile JSON field name: `publications`. Month and enum formats are unchanged.

## Workflow follow-up

- Review the diff, run the full backend build and UI checks, then build and deploy backend and UI with the lead (`handoff/README.md`).
