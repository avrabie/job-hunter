# Design

## Context

`CvReader.profileFrom(text)` makes one structured request for the whole profile (`CvProfileDraft`). The UI applies the draft with `applyDraft` (keeps preferences). `file-cabinet` provides the master CV through `FileCabinet.masterCv(sub)`; `profile-publications` adds `Publication` and `PublicationDraft`.

## Goals / Non-Goals

**Goals:** complete extraction of one section on demand, cheaper and more detailed than the whole-profile request. **Non-Goals:** merging with existing entries (the user replaces after confirming), extracting skills or languages per section (later), choosing the model per section.

## Decisions

- **Public API:** `enum class ProfileSection { EXPERIENCE, EDUCATION, PUBLICATIONS }` and `CvReader.sectionFrom(text: String, section: ProfileSection): Profile`. The existing `profileFrom` is unchanged.
- **Typed answers:** three small `@Serializable` wrappers (`ExperienceSection(experience: List<ExperienceDraft>)`, `EducationSection`, `PublicationsSection`) reuse the existing draft entry types and the existing `DraftMapper` entry mappers, so value checks stay in one place.
- **Prompts:** one shared system text (the existing rules) plus a short section instruction. The experience instruction says to list every position and every achievement the CV states, in the CV's wording, most recent first; it is the reason for a separate request.
- **Response shape:** the existing `Profile` JSON with only the section filled, so the UI needs no new type; the UI copies the one field into the form.
- **Replace, not merge:** merging needs identity rules for entries (same employer and dates?) and is error-prone. The user sees the draft and the confirmation before replacing.
- **Limit:** a section request uses the same hourly counter as the whole-profile request.
- **Master known to the page:** `ProfilePage` loads `GET /api/files/master` once and the cabinet reports changes through a callback, so the section buttons and the whole-profile button agree.

## Risks / Trade-offs

- Each section request is a model call; five per hour is enough for a review session but not for experiments. The limit is a setting.
- A section answer can disagree with the whole-profile draft (wording, order). Both are drafts for review.
