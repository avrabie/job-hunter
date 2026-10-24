# Report: export-profile-cv, tasks 2.1-2.3 (cx-model)

Worktree: `.claude/worktrees/agent-a2d91c5e7cfb66705`. Nothing committed.

## Tasks finished

- **2.1** Document model and enums. `./gradlew compileKotlin --offline` exit 0.
- **2.2** Labels and CvBuilder. `./gradlew test --offline --tests '*CvBuilder*' --tests '*Labels*'`:
  CvBuilderTests 16 run, 0 failed; LabelsTests 11 run, 0 failed.
- **2.3** Markdown renderer. `./gradlew test --offline --tests '*Markdown*'`:
  MarkdownRendererTests 5 run, 0 failed (English and German golden output, injection, escaping, same bytes for all three templates).

All three were run together at the end: 32 tests, 0 failures. Checkboxes 2.1, 2.2 and 2.3 are ticked in `openspec/changes/export-profile-cv/tasks.md`.

## Tasks not finished

None.

## Files

Created:
- `backend/src/main/kotlin/net/s4v3/backend/cv/export/ExportTypes.kt`
- `backend/src/main/kotlin/net/s4v3/backend/cv/export/CvDocument.kt`
- `backend/src/main/kotlin/net/s4v3/backend/cv/export/internal/Labels.kt`
- `backend/src/main/kotlin/net/s4v3/backend/cv/export/internal/CvBuilder.kt`
- `backend/src/main/kotlin/net/s4v3/backend/cv/export/internal/MarkdownRenderer.kt`
- `backend/src/test/kotlin/net/s4v3/backend/LabelsTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/CvBuilderTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/MarkdownRendererTests.kt`
- `handoff/cx-model.report.md` (this file)

Changed:
- `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt` (group 1 overlap, see below)
- `openspec/changes/export-profile-cv/tasks.md` (ticks 2.1-2.3 only)

## Group 1 overlap: Contact added to Profile.kt (lead resolves at merge)

- Added `data class Contact(fullName, headline, email, phone, website, linkedin: String? = null)` with a KDoc line, placed above `Location`.
- Added `val contact: Contact = Contact()` as the first field of `Profile`.
- `full-profile.json` is not changed. Tests set contact with `copy(contact = ...)`.
- Group 1's validator and fixture work is not in this worktree.

## Decisions to review

1. `CvRenderer` is defined in `cv/export/CvDocument.kt`, since MarkdownRenderer needs it in group 2. Group 3 must not redefine it. The stand-in `StandInDocumentModel.kt` is not needed here.
2. `ExportOptions.sections` has no default, exactly as in the Contract. The booleans have the Contract's defaults.
3. Structure choices not fixed by the Contract:
   - Experience: heading = employer, subheading = title, period, place, bullets = achievements, tags = technologies.
   - Open source: heading = repo name, link text = owner/repo, subheading = language and stars, text = description. The user's note and, when `includeAnalysis`, the analysis summary go into bullets. Tags = analysed tech stack names.
   - Education: heading = institution, subheading = degree and field.
   - Publications: heading = title, subheading = authors, period = year, place = venue, link = url.
   - Skills: TagSection, "Name (Level)" when a level is set.
   - Languages: LineSection, "English – Advanced".
   - Work authorisation: a fact, "Work authorisation: <words>".
   - Contact lines: email, phone, "Zurich, ZH", website, LinkedIn, then "github.com/<user>" linked to https://github.com/<user>.
4. Wording words are my choice and should be reviewed by the lead:
   - Skill levels: Beginner/Grundkenntnisse, Intermediate/Gute Kenntnisse, Advanced/Fortgeschritten, Expert/Expertise.
   - CEFR levels: A1 Basic, A2 Elementary, B1 Intermediate, B2 Upper intermediate, C1 Advanced, C2 Proficient; German Grundkenntnisse, Elementar, Mittelstufe, Gute Kenntnisse, Fortgeschritten, Fliessend; NATIVE Native/Muttersprache.
   - Permits: e.g. "Swiss permit B (residence)" / "Aufenthaltsbewilligung B".
   - Period with a missing start prints only the end month. Education with no end prints "Present"/"heute", as the spec says for no end month.
5. Markdown escaping:
   - Escapes `\ ` * _ [ ] < > | ~` everywhere in a line.
   - At line start, escapes `# - + =` and ordered markers such as `1.` or `2)`.
   - Entry headings use `###`.
   - Link destinations percent-encode `( ) < > \` and whitespace; only http(s) becomes a link.
6. Tests read the fixture with `ProfileJson.read` (internal, same module), not through the DB-backed service.
7. A bug found and fixed during the run: a period such as "03.2021" was escaped as an ordered-list marker when each meta part was escaped separately. The meta line is now escaped once as a whole. The German golden test caught it.

## Surprising / for the lead

- Labels and words above need a native-speaker check before the PDF review.
- Tests ran offline with `--offline`; no network use, no commits, no build pushes.
