# Design

## Context

`Profile` is one JSON document per user (`profile.document`, JSONB). Reading is lenient (a missing field takes its default), requests are parsed strictly, and `ProfileValidator` holds the rules and limits. The UI mirrors the model by hand in `ui/src/profile/types.ts`, and both sides share a sample document (`full-profile.json` in each test tree, kept equal by a drift test). The CV draft is a string-based DTO (`CvProfileDraft` and the section wrappers) mapped by `DraftMapper`, and sections are extracted through `CvReader.sectionFrom` with `ProfileSection`.

## Goals / Non-Goals

**Goals:** technologies on positions, a Projects section, both read from a CV and editable in the page, with no migration.

**Non-Goals:** importing from GitHub (a later change), changing the matching score, highlight on positions, a profile preview, a controlled technology catalogue.

## Decisions

- **Model:** `Experience` gains `technologies: List<String> = emptyList()`. New `Project(name, type: ProjectType, context?, start: YearMonth?, end: YearMonth?, description?, technologies, results, links: List<ProjectLink>, highlight: Boolean = false)`, `ProjectLink(label, url)` and `enum ProjectType { ASSIGNMENT, PERSONAL, OPEN_SOURCE, WORK }`. `Profile.projects` defaults to empty. Old documents read with the defaults, so there is no migration (same as publications).
- **One list with a type** (owner's choice): assignments, personal and open-source projects share fields; the type can later be shown, grouped or filtered.
- **Results as lines:** `results` is a list of short strings like `achievements`, not structured metrics: the CV text is prose and structured metrics would be guessed.
- **Free tags with suggestions** (owner's choice): the server only checks blank, length and count of tags and never changes them. Tidying (trim, drop duplicates that differ only in case, first spelling wins) happens in two places that produce them: the UI tag input and `DraftMapper`. Suggestions come from the skills list and from tags already used, computed in the browser.
- **Link safety:** the same absolute `http`/`https` check as publication links, applied to `projects[i].links[j].url`, with a label required so a link is never anonymous.
- **Highlight:** a boolean on projects only. The model never sets it; the user does. It exists for the tailoring step to prefer, and costs nothing now.
- **Draft types:** `ExperienceDraft.technologies: List<String>`; `ProjectDraft(name, type: String?, context, start, end, description, technologies, results, links: List<ProjectLinkDraft>)` with strings for type and months, like the other drafts, so one bad value never fails the whole answer. An unknown type becomes `PERSONAL` (the least specific honest choice) rather than dropping the project.
- **Sections:** `ProfileSection.PROJECTS`, a `ProjectsSection` wrapper, one more instruction text in `KoogCvReader` ("every project, assignment or open-source work the CV states, with the technologies and results it names"), and `projects` accepted by the controller. The UI's `ProfileSectionName` gains `'projects'`, and the page's fill logic is already generic per section.
- **Body limit:** the profile request-body cap rises from 128 KB to 256 KB (`ProfileService.MAX_BODY_BYTES`, the 413 test and the spec). The per-field limits (50 projects of up to about 15 KB each) can in theory exceed even 256 KB; the body cap is the ceiling that protects the server, and ordinary profiles are far below it. No gateway or edge setting limits this body.
- **UI:** a reusable `TagInput` (chips, Enter or comma adds, Backspace on an empty input removes the last, a `datalist` for suggestions) used on positions and projects; a Projects section built like Publications, with a small links editor (label and address, at most five).
- **Fixture:** both `full-profile.json` files get the same sample of a position with technologies and one project, so the drift test keeps proving the two models agree.

## Risks / Trade-offs

- The CV reader now has more to get right; the review step before Save stays essential, and the section buttons let one part be redone.
- Free-text tags can contain typos; tidying and suggestions reduce but do not remove them.
- A larger form is more to scroll; the page may need collapsible sections later.
