# Tasks

Rules for every task (see `handoff/haiku-rules.md`): edit only the files named, copy the pattern of the existing code named, run the verify command, tick the box, and stop and report if blocked. Do not start before `profile-publications` and `file-cabinet` are done and merged.

## 1. Backend

- [x] 1.1 In `backend/src/main/kotlin/net/s4v3/backend/llm/CvReader.kt` add `enum class ProfileSection { EXPERIENCE, EDUCATION, PUBLICATIONS }` and `fun sectionFrom(text: String, section: ProfileSection): Profile` to the interface. In `llm/internal` add the three wrapper DTOs next to `CvProfileDraft.kt` (copy `CvProfileDraft`'s annotations; reuse `ExperienceDraft`, `EducationDraft`, `PublicationDraft`), add mapper functions in `DraftMapper.kt` that reuse the existing private entry mappers, and implement `sectionFrom` in `KoogCvReader.kt` by refactoring the shared call (prompt building, executor, timeout, error handling) into one private function that takes the target type and the extra instruction. Keep `profileFrom` behaviour identical. Verify: copy the `KoogCvReaderTests` style to add tests for the four `llm` scenarios (only the requested section, complete entries with six achievements, bad month drops the entry, unparseable fails with the fixed error); the whole `llm` test package and `KoogBoundaryTests` pass.
- [x] 1.2 In `cv/internal/CvExtractController.kt` accept `@RequestParam(required = false) section: String?`, map it case-insensitively to `ProfileSection` (unknown value: 400 with a fixed message before anything else, no model call), call `sectionFrom` for a section and `profileFrom` otherwise, and update the OpenAPI text. Verify: extend `CvExtractApiTests` (the scripted `CvReader` gets `sectionFrom` too) for the four `cv` scenarios and check the hourly limit counts sections; `./gradlew build` passes.

## 2. UI

- [ ] 2.1 In `ui/src/cv/cvApi.ts` add `extractSection(auth, section)` calling `POST /api/cv/extract?section=<section>` (type `ProfileSectionName = 'experience' | 'education' | 'publications'`), returning the same result kinds and fixed messages as `extractProfile`, including 400 ("That section cannot be read.") and 409 ("Choose a master CV first."). Verify: add cases to `cvApi.test.ts` (query string, each status).
- [ ] 2.2 Make the page know the master: in `ProfilePage.tsx` load `GET /api/files/master` once (helper in `ui/src/files/filesApi.ts`), keep `hasMaster` in state, pass it to the cabinet (`onMasterChange`) and to `ProfileForm`. Add a "Fill from CV" button to the Experience, Education and Publications headings in `ProfileForm.tsx` (props: `canFillFromCv`, `onFillSection(section)`, `fillState` per section for loading and error), implemented in `ProfilePage.tsx`: confirm when the section has entries, replace only that field with the returned one, notice that it is not saved. Verify: component tests for the four `ui-profile` scenarios here (fill one section, confirmation and decline, no master, 422/429), then `cd ui && npx vitest run && npx tsc -b && npx vite build && npx oxlint --deny-warnings && npx prettier --check .` pass.

## 3. Docs and check

- [ ] 3.1 Document the `section` parameter in `backend/README.md` and the buttons in `ui/README.md`; add section notes to `docs/koog-notes.md` (why a separate request, how the wrappers reuse the entry types). Verify: `grep -n section` shows each file.

## Contract (do not change)

`POST /api/cv/extract?section=experience|education|publications` answers a `Profile` JSON with only that field filled. Unknown section: 400 `{"error":"invalid_cv","message":"That section cannot be read."}`. No master: 409 `{"error":"invalid_cv","message":"Choose a master CV first."}`.

## Workflow follow-up

- The lead reviews the diff, builds and deploys backend and UI, and compares real drafts for the user's own CV with the whole-profile draft. Then archive the change.
