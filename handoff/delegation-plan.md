# Delegation plan: publications, file cabinet, section fill

Three OpenSpec changes (all validated):
- `profile-publications`: publications in profile, draft and page.
- `file-cabinet`: many files up to 10 GiB, master CV, ticket downloads, disk storage on a PVC.
- `cv-sections`: "Fill from CV" per section (needs the other two).

Agents are Haiku (`model: haiku`), each in its own git worktree so that Gradle builds and half-finished edits do not disturb
each other. They get `handoff/haiku-rules.md` plus their task ids. The lead merges worktrees, runs the full builds and
deploys. Only the lead touches the cluster.

## Wave 1 (parallel)

| Agent | Change and tasks | Owns |
|---|---|---|
| `pub-backend` | profile-publications 1.1, 1.2, 1.3, 2.1 | `backend/.../profile/**`, `backend/.../llm/internal/**`, `backend/src/test/**/profile*`, `backend/src/test/resources/profile/` |
| `pub-ui` | profile-publications 3.1, 3.2 (4.1 docs after both) | `ui/src/profile/**` |
| `files-core` | file-cabinet 1.1, 1.2, 1.3, 2.1, 2.2 | `backend/.../files/**`, `cv/internal/CvContent*`, `db/changelog/008-files.sql`, master changelog, `DatabaseBaselineTests` |
| `files-ui` | file-cabinet 7.1, 7.2, 7.3 (mocked API per the contract) | `ui/src/files/**`, `ui/src/cv/**`, `ui/src/profile/ProfilePage.tsx` wiring |

Known overlap: `ui/src/profile/ProfilePage.test.tsx` and `ProfilePage.tsx` are touched by `pub-ui` and `files-ui`; the lead
resolves that merge. The UI drift test compares the two `full-profile.json` fixtures; it is red until `pub-backend` task 1.3
is merged with `pub-ui`.

## Wave 2 (after wave 1 is merged and reviewed)

| Agent | Tasks |
|---|---|
| `files-api` | file-cabinet 3.1, 3.2, 4.1, 4.2 (**3.1 and 4.2 are risky**: the lead reviews right after) |
| lead | file-cabinet 8.1 (PVC), 8.2 (measure upload limits on the cluster) |

Then `files-finish`: file-cabinet 5.1, 5.2, 5.3, 6.1, 6.2; lead 8.3 docs. Merge, full build, deploy, check a large upload.

## Wave 3

`sections-backend` (cv-sections 1.1, 1.2) and `sections-ui` (2.1, 2.2) in parallel, then docs 3.1 and deploy.

## Honest limits of the cheaper model

- The streaming upload (file-cabinet 3.1) and the ticket download with the security change (4.2) are the delicate parts. If
  a Haiku agent's result needs more than one correction round, the lead does that task or hands it to a stronger model.
- Each agent is told to stop and report when the code differs from the task. Expect a few such reports; they are cheaper
  than wrong guesses.
