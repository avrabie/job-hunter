# readme report

Wrote root `README.md`. Nothing committed; no source or other README touched.

## Verified by running
- `./gradlew tasks --offline` in gateway (wrapper works, JDK 25 present locally).
- `git check-ignore`: `backend/.env` is ignored (root `.env` rule). `private/` is in the root `.gitignore` (read).
- `which`: node, java 25 and docker present; pnpm is NOT installed on this machine.

## Only read (not run)
- All start commands, ports, health URLs and variable names: application*.yaml, compose files, .env.example, module READMEs, k8s/base.
- Versions: JDK 25 (toolchain), Gradle 9.8.1 (wrapper), Node >=22, pnpm 12.14.0 (package.json).
- Troubleshooting items come from the READMEs; the "works without a key" list is my inference from them.

## Gaps
- Auth health URL: auth has context path /auth, so I wrote `/auth/actuator/health/readiness`; its README says `/actuator/...` under /auth. Not run.
- `JOBHUNTER_LLM_MODEL` and `JOBHUNTER_FILES_DIR` rely on Spring relaxed binding; untested.
- `jobhunter.files.dir` defaults to `/data/files` and no doc says how a dev gets a writable path locally (or whether the dev profile overrides it). Local uploads may fail; unverified.
- Whether `ANTHROPIC_API_KEY` in `backend/.env` works is unverified (.env is imported as properties); README says to export it in the shell.
- backend README says extraction limits 30000 chars / 5 per hour in one place, application.yaml has 120000 / 20. README avoids the numbers; backend README needs a fix.
- `APP_ADMIN_EMAILS` has no dev alias (auth README: "dev: none"), and the admin grant needs real Google credentials; the seeded admin@example.com is the local alternative.
- Google local redirect URI (`http://localhost:8080/auth/login/oauth2/code/google`) is derived from the auth README pattern; with UI-proxy issuer it may need localhost:5173 instead. Untested.
- No Makefile, root docker-compose, ui `.env.example`, or lint/format task for the Gradle projects.
- `k8s/overlays/local` (kind) has no step-by-step doc and k8s/README says no cluster uses it; only linked.
- ui/README still says "backend has no endpoints yet" and "Checked on 2026-10-21", outdated relative to its later sections.
- Backend dev profile reads `.env` only when its working directory is `backend/`.
