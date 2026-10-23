# Design

## Context

`Profile` (Kotlin data class) is stored as one JSON document; reading is lenient (missing fields take defaults), writing and request parsing are strict. `ProfileValidator` holds the rules and size limits. The UI mirrors the model by hand in `ui/src/profile/types.ts` and shares a sample document with the backend (`full-profile.json` in both test trees; `profile.test.ts` fails if they drift). The CV draft is a string-based DTO mapped by `DraftMapper`.

## Goals / Non-Goals

**Goals:** a Publications section end to end (model, validation, draft, page) with no migration.

**Non-Goals:** DOI lookups, citation formats, importing from external databases, publication types.

## Decisions

- **Shape:** `Publication(title: String, venue: String? = null, year: Int? = null, authors: String? = null, url: String? = null)`. `year` instead of a month because CVs rarely give more. Authors are free text because name formats vary.
- **No migration:** `Profile.publications` defaults to `emptyList()`. Old stored documents and old clients that do not send the field keep working.
- **Link safety:** the validator accepts only `http://` and `https://` absolute URLs (parsed with `java.net.URI`, host required). The UI renders links elsewhere only through the existing `safeHttpUrl`.
- **Year upper bound:** current year plus one (forthcoming papers). The validator reads the clock; tests use the current year relative to `LocalDate.now()`.
- **Draft DTO:** `PublicationDraft(title, venue?, year: String?, authors?, url?)`. `year` is a string in the DTO so that "2021" or "n.d." never fails the whole answer; the mapper parses an integer or yields null.
- **Fixture:** both `full-profile.json` files get the same publication sample so the drift test keeps proving the two models agree.

## Risks / Trade-offs

- A model may put a DOI in `url` as `doi:...`; that is not http(s), so it becomes null. Acceptable; DOI links are a later improvement.
