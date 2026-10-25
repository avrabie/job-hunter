# Tasks

## 1. Backend (llm, cv)

- [ ] 1.1 `ProfileSection.CONTACT`, `ContactSection` draft in `SectionDrafts.kt`, prompt instruction (candidate's own details only), `DraftMapper.toProfile(ContactSection)`; `KoogCvReader.sectionFrom` handles it. Controller accepts `contact` (case-insensitive), docs/OpenAPI text updated.
- [ ] 1.2 `ContactGrounding` (llm/internal): drop values not in the CV text, add `https://` to bare links, apply the save-time email/phone/link rules; called after mapping in the contact path. Unit tests with synthetic data (Ada Example): kept, dropped, injected text, phone with separators, link without scheme.
- [ ] 1.3 `CvExtractApiTests`: contact returned with FakePromptExecutor; invented email dropped; 400 for unknown section still; marker (unique email) never in logs; same 409/422/429/502/503 paths.
- [ ] 1.4 Docs: `backend/.../cv/README.md`, `docs/cv-export.md` or `docs/architecture.md` where sections are listed.

## 2. UI

- [ ] 2.1 `analysisApi.ts`: section type includes `contact`; `ProfilePage.tsx`: `onFillContact` with the fill-empty / confirm-overwrite rule (labels only in the dialog), notice, nothing-found message, fill state; `ContactSection.tsx`: the button via `FillFromCv`, disabled hint.
- [ ] 2.2 Tests: empty form filled, conflicting field + confirm yes/no, nothing found, no master, error messages, no profile request sent.

## 3. Wrap-up

- [ ] 3.1 `./gradlew test integrationTest`, UI checks (vitest, tsc, oxlint, prettier), `openspec validate --all`.
- [ ] 3.2 Deploy backend and ui only after the owner says so; archive after the owner tests on prod.
