# pcv-g8 report (task 8.1, documentation only)

## Result
- 8.1 done and ticked in `openspec/changes/public-cv-page/tasks.md`. No code, specs or tests touched. Not committed.
- `grep -n -i "public cv" backend/README.md ui/README.md PLAN.md` shows all three (backend/README.md:267, ui/README.md:109, PLAN.md:67).
- `docs/cv-public.md` exists (136 lines).
- Config keys, all present in `backend/src/main/resources/application.yaml` under `jobhunter.public-cv` (lines 62-68):
  page-per-ip-per-minute 60, not-found-per-ip-per-minute 10, page-per-handle-per-minute 600, handle-checks-per-hour 60, previews-per-hour 30.
- `PUBLIC_CV_PATH = '/cv/'` confirmed in `ui/src/publiccv/options.ts`; `/publish` route confirmed in `ui/src/App.tsx`.

## Files
- `backend/README.md` (new "Public CV" section, before "Project analysis")
- `ui/README.md` (new "Public CV page" section, before "File cabinet")
- `docs/cv-public.md` (new)
- `PLAN.md` (one sentence in the `cv` row)
- `README.md` (bullet in "More documentation")
- `openspec/changes/public-cv-page/tasks.md` (8.1 ticked)
- `handoff/pcv-g8.report.md` (this file)

## Differences between design and code (documented as code; for the lead)
1. Digits-only handles: design decision 3 lists them under reserved; code and spec give 400 `validation_failed` (`fields.handle`), not 409 `handle_reserved`.
2. Account deletion: the `public_cv_handle_hold` rows cascade with the account, so a held handle is free at once on deletion. The design is silent; docs say so. Decide whether holds should survive deletion.
3. Client IP: the backend takes the first `X-Forwarded-For` value. `k8s/platform/traefik-values.yaml` has no forwarded-header setting, so whether the edge replaces or appends the header is unverified. A client may be able to spoof the first value; the per-handle cap is the backstop. Docs say "best effort".
4. Edge `/cv` rewrite: still unverified on the cluster (k8s/README 6.3 table is empty); docs say so.

## Not done / not verified
- Nothing deployed or run; no browser or cluster checks (per memory rule). Docs state facts from code reading only.
- The threat table in `docs/cv-public.md` is condensed from `design.md`, not copied.
