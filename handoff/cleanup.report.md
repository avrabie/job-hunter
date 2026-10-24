# Cleanup report

## Part 1: agent worktrees (42 in scope)

Removed 42 (worktree and branch `worktree-<dir>` deleted, `git log main..branch` empty before each):
- 8 empty, clean status: a087c12d35100ca4d, a30d15265e2cabfc2, a37071756be01e8e2, a3834fbbe2ee0d477,
  a3d468ffa33547415, a5e70ca9f10824f97, a94c40f74cb9638cd, aeefc71df1ee4fc16
- 5 docs/README/handoff-only, identical to main: aabb596f76612d02b, a358309627423601d, a7ccc268dfc35f506,
  a7d4c6e8c3cb3629e, ac725645c0a008374
- 16 only untracked openspec/changes/github-open-source-projects/ (confirmed by lead): a100f05542001ec9e,
  a176e88d8ac2ba5ce, a3714e9e1d4423965, a614112899d97b497, a6326a150aa13a20d, a720609eae24e7e7f,
  a84265461573e8f5e, a8e446740295f6f07, ab69450fa3ecfcc83, abfb88a14ebb1d03c, acffee7c4dd663bd7,
  aec6509b48a0e25df, af4a83a4739b37e80, af76fe45fd2c0e279, afdbf864de33319c3, afeda9793033d94c5
- 11 only tasks.md checkbox ticks (diff checked: no other lines changed): a2d91c5e7cfb66705,
  a3d0c15f1929a358a, a5a6f310e50d84962, a5d3832cb97c0824f, a65e1ef27a9efc75e, a6c93439e5e390ae8,
  a89d3459362049af9, a96eb1c50b5980d85, aaa38563b24cf8b7e, ace3b5edf7619d6bf, ae1f8228ade526f40
- a8e23dd2c75a86ad1: only untracked docs/deprecations.md (confirmed by lead)
- a97e4fe104cb6197e: only untracked README.md and handoff/readme.report.md. The report was copied to
  main handoff/readme.report.md first (cmp OK). Root README.md discarded (confirmed by lead).

Kept 0 of the 42. Final `git worktree list` (after prune): 6 lines = main + 5 worktrees, all untouched:
- agent-a313a85395e48affd, agent-a6b0d39b26bc3170d, agent-aae7d02291a21e854 (out of scope, locked)
- agent-ad5be7eae804e5640 (out of scope, not in the 42)
- agent-a40ac9cf6c3052063 (not in the 42, locked)

## Part 2: fakeXhr references

Edited only ui/src/README.md (test/ list now includes fakeXhr.ts) and docs/architecture-auth-gateway-ui.md
(line 212 says `src/test/`; line 404 points to `ui/src/test/fakeXhr.ts`).

`grep -rn fakeXhr ui/src/README.md docs/`:
```
ui/src/README.md:17:- `test/`: `setup.ts`, `fakeAuth.ts`, `fakeManager.ts`, `fakeXhr.ts`.
docs/architecture-auth-gateway-ui.md:212:| `files/` | `FileCabinet`, `filesApi`, `format`, `fakeXhr` (test helper, lives in `src/test/`) |
docs/architecture-auth-gateway-ui.md:404:9. **`ui/src/test/fakeXhr.ts` is a test helper** (it now lives in `src/test/` ...
docs/simplification-candidates.md:40: ... `ui/src/files/fakeXhr.ts` ...   (outside the two allowed files; not changed)
```

No commits or pushes by this agent.
