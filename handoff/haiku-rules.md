# Rules for implementation agents (cheaper models)

You implement tasks from an OpenSpec change in `openspec/changes/<change>/`. The lead has already decided the design; your job is
to follow the task text exactly and prove it with tests.

1. **Read only** `openspec/changes/<change>/tasks.md` (your tasks), the spec files and `design.md` of that change, and the
   files the task names. Do not read `PLAN.md`, archived changes or other agents' files unless a task names them.
2. **Edit only** the files a task names or clearly implies (new files next to the named ones). If you think another file must
   change, stop and report it; do not edit it.
3. **Copy patterns.** Where a task says "copy X", open X and follow its structure, naming, comment density and test style. Do
   not invent new patterns, libraries or abstractions. Do not refactor code you were not asked to change.
4. **Verify exactly as written.** Run the verify command of the task and read the output. A task is done only when its tests
   pass. Report the numbers (tests run, failures). If a test fails and you cannot fix it in two honest attempts, stop.
5. **Tick the checkbox** (`- [ ]` to `- [x]`) of a task only when its verify command passes. Never tick a task you did not
   finish, and never delete or weaken a test to make it pass.
6. **Never** commit, push, build or push images, run `kubectl`, `docker`, or anything that contacts the cluster or Anthropic.
   Never put secrets, tokens, CVs or personal data in code, tests or reports. Test data is synthetic.
7. **Logging:** no log line may contain file names, file content, CV text, profile text, tickets or tokens.
8. **Stop and report** (do not guess) when: the task is unclear, the code differs from what the task describes, a design
   decision is missing, or a change outside your named files seems necessary. Say what you found and what you need.
9. Work in your own git worktree if you were given one; do not touch other worktrees.

## Report (`handoff/<agent-name>.report.md`, under 80 lines)

- Tasks finished (ids), with the verify command output summary (counts).
- Tasks not finished and why.
- Files created or changed (paths only).
- Anything surprising, and decisions you need from the lead.
