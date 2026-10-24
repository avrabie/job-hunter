# Report: an-digest (tasks 2.1 and 2.2)

## Tasks finished

- **2.1** `RepositoryDigestSourceTests`: 7 tests, 0 failures.
  Run: `./gradlew test --tests '*RepositoryDigest*'`.
- **2.2** `GitHubDigestProviderTests`: 17 tests, 0 failures.
  Run as part of the same Gradle invocation.
- Regression check: `./gradlew test` in `backend/` is BUILD SUCCESSFUL. 455 tests in total, 0 failures, 0 errors, 0 skipped (includes ModularityTests).
- Regression check on neighbours: `GitHubRepositoryProviderTests` 12/12, `RepositoryCatalogTests` 7/7.

Tasks 2.1 and 2.2 are ticked in `openspec/changes/analyze-open-source-projects/tasks.md`. Not committed.

## Tasks not finished

None.

## Files created

- `backend/src/main/kotlin/net/s4v3/backend/profile/RepositoryDigest.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/DigestResult.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/RepositoryDigestSource.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/RepositoryDigestProvider.kt` (internal seam, same role as `RepositoryProvider`)
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/DefaultRepositoryDigestSource.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/GitHubDigestProvider.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/RepositoryDigestSourceTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/GitHubDigestProviderTests.kt`
- `handoff/an-digest.report.md` (this file)

## Files changed

- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/RepositoriesSettings.kt`: added `GitHub.rawBaseUrl` (default `https://raw.githubusercontent.com`) and a new nested `Digest` class with the caps. Keys: `jobhunter.repositories.github.raw-base-url`, `jobhunter.repositories.digest.max-tree-body-bytes` (8 MB), `.max-tree-paths` (400), `.max-tree-depth` (3), `.max-readme-bytes` (20 KB), `.max-manifests` (8), `.max-manifest-bytes` (12 KB).
- `openspec/changes/analyze-open-source-projects/tasks.md`: ticked 2.1 and 2.2 only.

Not touched: `Profile.kt`, `ProfileValidator`, the llm module, the web module, the UI.

## Decisions beyond the literal text (please confirm)

1. **Dot segments refused.** `owner` or `repo` equal to `.` or `..` is `InvalidName`, in addition to the `[A-Za-z0-9._-]{1,100}` rule. Without this, the spec's `../x` case would pass the character class. The request paths are built from the validated names.
2. **Manifests and README chosen from the kept 400 paths.** This keeps them consistent with the analyst's evidence check, which accepts only paths of the digest's tree. A manifest beyond the 400 cut is never read.
3. **`truncated` only for a provider truncation or a cut to 400.** Entries deeper than 3 are dropped silently, as designed, and do not set the flag.
4. **Tree body above 8 MB is `Unavailable`**, not cut. A cut JSON cannot be parsed, so this follows the listing provider's body-cap behaviour.
5. **File text is cut at bytes** (`readNBytes`), then decoded as UTF-8. A multi-byte character split at the cut becomes a replacement character.
6. **A file whose download fails** (any non-200, I/O error, timeout) is skipped. A README that fails gives `readme = null`.

## Things the lead needs to know

- **`RepositoryDigest` has no README path.** `readme` is only the text. The response `files` list (group 4) must recover the README path from `paths` (a depth-1 blob whose name starts with `README`, ignoring case, first in tree order). The digest does not carry it, and the Contract is fixed, so I did not add a field. Decide whether group 4 derives it or whether the Contract gains `readmePath`.
- The `Found` variant's field is named `digest` (`DigestResult.Found(digest: RepositoryDigest)`). The Contract only gives the type, so this is a naming choice.
- The test server in `GitHubDigestProviderTests` needs a thread pool. With a single dispatcher thread, a delayed response blocks the next request and produces a false timeout. This is a test-harness note only, not a product issue.
- `DefaultRepositoryDigestSource` caches `Found` for 10 minutes, keyed by provider and lower-cased full name, with the same bounded-map behaviour as `DefaultRepositoryCatalog`.
