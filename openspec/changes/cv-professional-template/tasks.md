# Tasks

Rules for every task (see `handoff/haiku-rules.md`): edit only the files named, copy the pattern of the existing code named, run the verify command, tick the box, stop and report if blocked. Backend commands run in `backend/` (test tiers: `./gradlew test` is the fast tier; API and Spring tests are integration-tagged and run with `./gradlew integrationTest --tests "<Class>"` using double quotes; other agents run Gradle at the same time, so run only the classes you touch; the lead runs `verify` at the end), UI commands in `ui/` (`npx`; install with `npx --yes pnpm@12.14.0 install --frozen-lockfile` if `node_modules` is missing, never commit it). The Contract in `design.md` is binding. Test data is synthetic (an invented "Ada Example", photos drawn by the test itself); **never open, read or quote `private/sample/cv-sample.pdf` or any real personal data** and never use a real face. Everything must also work in the Linux amd64 image: no OS-specific tools, fonts or native libraries.

Groups 1, 2, 3 and 5 are independent and can run in parallel; group 4 needs 1, 2 and 3 merged; group 6 needs 4; group 7 last.

## 0. Checks by the lead

- [ ] 0.1 Check `javax.imageio` JPEG read and write inside the production backend image (run a tiny Java/Kotlin test with the image's JRE as done for the PDF renderer) and record the result in `design.md` Risks.
- [ ] 0.2 Compare the sample's look to the rendered Professional PDF (render pages to PNG locally with `sips` for inspection only, never committed) and list differences as follow-ups.

## 1. File cabinet API (backend, `files`)

- [x] 1.1 Add `FileCabinet.image(sub, id): CabinetFile?` and implement it in `FileCabinetService` per design decision 7 (owner scoped lookup, 6 MB cap, first three bytes `FF D8 FF`, null otherwise, no logging of names). Tests in a new `FileCabinetImageTests` (integration-tagged like the neighbouring files tests): own JPEG found; other user's id null; PNG named `x.jpg` null; PDF null; oversize null; unknown id null. Update `files/README.md` and `openspec`-independent docs line in `docs/architecture.md` only if it lists the API.

## 2. Photo processor (backend, `cv.export`)

- [x] 2.1 Add `PhotoProcessor`, `InvalidPhotoException` and `CvPhoto` per design decision 4 (public API in `net.s4v3.backend.cv.export`, implementation internal). Tests (fast tier, no Spring): a generated 1000x600 JPEG gives 320x320 output, the output is a JPEG, the four corners are white, the centre keeps the colour, determinism (same input same bytes), an EXIF-bearing JPEG (write one with a metadata node via `ImageIO`) gives output with no `APP1` segment, a truncated file, a PNG, a 13 000 px wide header-only JPEG and a 7 MB file all raise `InvalidPhotoException` without reading the whole image for the oversize cases, and a CMYK/greyscale JPEG converts.

## 3. Skill groups and labels (backend, `cv.export`)

- [x] 3.1 Add `SkillGroups` and the `SkillGroup` enum per design decision 3, the nine EN and DE labels in `Labels` (match the style of the existing labels; German: Programmiersprachen, Frameworks, Messaging, Sicherheit & Identität, Cloud & Container, Tests & Observability, Datenbanken, Werkzeuge, Weitere), and tests pinning the spec examples plus whole-token matching (`Javascript` is not `Java`, `C` is not inside `Docker`), `PL/SQL`, `C++`, case-insensitivity, first-group-wins, unknown to OTHER.

## 4. Professional template (backend, `cv.export`)

- [x] 4.1 Add `CvTemplate.PROFESSIONAL`; `ExportOptions.photoFileId`; `CvDocument.photo`; `GroupedTagSection`/`TagGroup`; `CvBuilder` builds grouped skills for PROFESSIONAL only and takes the photo bytes as a parameter (the builder stays pure: the service reads the photo). Before changing anything, add golden tests that capture today's HTML of CLASSIC, MODERN and COMPACT for the synthetic Ada profile (stored as test resources) and make them pass; they must still pass after the change.
- [x] 4.2 `Templates` PROFESSIONAL block, `HtmlRenderer` header table with the photo, entry table layout, grouped skills table, CSP `img-src data:` only with a photo, bold lead-in rule, footer for PDF (`PdfRenderer`/CSS per design decision 5; record which footer technique worked). Tests: HTML contains the colours, the section headings, the group rows, the `data:image/jpeg` URI exactly once with a photo and not at all without one and no `img-src` without a photo, escaping of hostile names, German labels; PDF test with and without photo (`%PDF-`, text contains name and footer on both pages of a long profile, one image with a photo, none without) and a size sanity check.
- [x] 4.3 `DefaultExportService` reads the photo through `FileCabinet.image` and `PhotoProcessor` only for PROFESSIONAL and non-Markdown; `InvalidPhotoException` for null/invalid. Controller: request field `photoFileId`, 400 `photo_invalid` using the existing error helper, OpenAPI text and the `ApiDocs` summary. API tests (integration): photo PDF, someone else's file, PNG as `.jpg`, ignored with CLASSIC, no photo in logs (use `captureAppLogs`), the 30-exports limit still counts.

## 5. UI (export page)

- [x] 5.1 `ui/src/export`: template option "Professional", photo picker per `ui-cv-export`, options type and request body, error message for `photo_invalid`. Tests in the style of `ExportPage.test.tsx` and `exportApi.test.ts`. Needs the Contract only; can run against the files API shape already in `ui/src/files/filesApi.ts` (read it for the list call and the `FileInfo` type).

## 6. Public page (backend and UI)

- [ ] 6.1 Backend: `PublicCvSettings.photoFileId`, validation on save (400 `photo_invalid`), template PROFESSIONAL accepted, photo passed to the builder, render cache key, audit flag `photo`, route `GET|HEAD /api/public/cv/{handle}/photo` per design decision 8, OpenAPI hidden or documented like the page route, docs `docs/cv-public.md` and `docs/cv-export.md`. Tests (integration, extend `PublicCvApiTests` or a new class): off by default; photo shown and route 200 with the headers; not the owner's file 400; deleted file gives page without photo and route 404; paused 404; unknown handle `/x/photo` identical to other 404; limits shared; HEAD; cache key changes when the file is replaced; photo and file id absent from logs and audit; the existing `/api/public/cv/**` catch-all tests still pass.
- [x] 6.2 UI: `ui/src/publiccv` template option, photo picker and warning, options and API types, tests like `PublicCvPage.test.tsx`.

## 7. Lead

- [ ] 7.1 Merge, full `./gradlew clean verify`, UI tests/tsc/oxlint/prettier, `openspec validate --all`, security review of the photo route and the processor (independent agent), then deploy only after the owner says so; archive after the owner tests.
