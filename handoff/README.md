# Agent handoff

Small, scoped contexts for parallel agents. Each agent gets one brief (`<name>.brief.md`), reads only the files the brief lists,
works only in the paths the brief allows, and reports in one file (`<name>.report.md`). Nobody commits or deploys: the lead
(the main session) reviews, commits and deploys.

- **Brief**: goal, files to read, paths it may edit, what "done" means, size limits for the report.
- **Report**: what was done or found, decisions needed, and `Needs` (e.g. an API the UI wants from the backend), each with the
  exact request/response shape wanted. Keep it under 120 lines, no personal data, no secrets.
- **Cross-agent needs** go in the report's `Needs` section; the lead turns them into an OpenSpec change for the owner of that
  area, and the other agent gets the finished contract, never the other agent's context.
- Agents do not read `openspec/changes/archive/`, `PLAN.md` or other agents' files unless the brief lists them.
