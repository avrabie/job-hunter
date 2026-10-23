# files-api report (file-cabinet tasks 3.1, 3.2, 4.1, 4.2)

## Tasks finished

- **3.1** `./gradlew test --tests '*FilesApi*'` passed: FilesApiTests 14 of 14 at that point, FilesApiDiskTests 1 of 1.
- **3.2** Same command passed: FilesApiTests 19 of 19 (list, details, master, owner checks).
- **4.1** Same command passed: FilesApiTests 24 of 24 (download with token, Range 206 with 10 bytes, delete, master cleared by delete).
- **4.2** Same command passed: FilesApiTests 28 of 28, FilesApiDiskTests 1 of 1 (ticket download once, bound/expired/unknown, 401 on all other endpoints).
- **Full build:** `cd backend && ./gradlew build` BUILD SUCCESSFUL. 364 tests, 0 failures, 0 errors, 0 skipped. ModularityTests 2 of 2 pass.

Tasks not finished: none of the four assigned.

## Files

Created:
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FilesController.kt`
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FileUploadGuards.kt` (`FreeSpaceGuard` filter, `FilesUploadLimitResolver`)
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/DownloadTickets.kt`
- `backend/src/test/kotlin/net/s4v3/backend/FilesApiTests.kt` (also holds `FilesVolumeInitializer` and `FilesApiDiskTests`)

Changed:
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FilesSettings.kt` (`maxOtherBytes`, `maxCvBytes`, `ticketSeconds`)
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FileStorage.kt` (creates `tmp` on start, `newTempPath()`)
- `backend/src/main/resources/application.yaml` (multipart 10GB and location, `jobhunter.files.dir: /data/files`)
- `backend/src/main/kotlin/net/s4v3/backend/accounts/internal/SecurityConfig.kt` (one line: `GET /api/files/*/content` permitAll)
- `openspec/changes/file-cabinet/tasks.md` (ticked 3.1, 3.2, 4.1, 4.2)

Not committed.

## Deviations (please review)

1. **Ticket check is in the controller, not in a filter.** The task asked for a small filter. A filter in `files.internal` cannot be used from `accounts.internal` without a module cycle, and a filter in `accounts` would need a fake JWT to make `CurrentUser` work. Instead `SecurityConfig` lets `GET /api/files/*/content` through (`permitAll`), and `FilesController.content` accepts the bearer token or a valid ticket and answers 401 otherwise. All other paths are unchanged.
2. **Disk-protection test is a second class**, `FilesApiDiskTests`, because `min-free-bytes` needs its own Spring context. It still matches `*FilesApi*`.
3. **Two new fixed texts** not in the Contract: unknown kind 400 "The kind must be CV or OTHER."; master of kind OTHER 400 "Only a file of kind CV can be the master CV.". Please confirm or change.
4. **Ticket is used up by any attempt** that reaches the check, including one for another file.
5. **Tomcat limit is decimal.** `max-file-size: 10GB` is 10 000 000 000 bytes, which is below 10 GiB (10 737 418 240). Files between the two are refused by Tomcat with the "10 GB" message. The task text said 10GB, so I kept it.
6. **Tomcat-level 413** is handled by a `HandlerExceptionResolver` (highest precedence) that answers only for `/api/files`. Other paths fall through to the existing `CvUploadErrors`, unchanged. Not covered by a test: MockMvc does not enforce the Tomcat limit.
7. **Directory creation failure only warns.** `FileStorage` creates `<dir>/tmp` on start, but if that fails it logs a warning (no path or file names) and the app still starts; uploads then fail. Tests run with the default `/data/files` without failing. Lead decision: fail fast instead?
8. **Other 507 and abort cases are not tested.** The "no space left" mapping and an aborted upload cannot be produced in MockMvc. The failed-insert case is tested with a test-only DB trigger that raises on one marker name, dropped afterwards. It checks no row, no stored file and an empty `tmp`.

## Unsure

- **CV uploads are spooled first.** Tomcat writes the whole body to the volume before the controller checks the 5 MB limit, so a large CV is written to disk before it is refused. Memory stays flat, since only a CV of at most 5 MB is read into memory. A `Content-Length` pre-check for kind CV would avoid this; I did not add it.
- **Missing `Content-Length`** is treated as 0 by the free-space guard.
- **Spring DEBUG logs.** Spring's own DispatcherServlet DEBUG logging may print the query string, which contains the ticket. The app's log lines do not. This matters for task 5.3 (`FilesLoggingTests`) and I have not checked it.
- **No unit test** for `FilesUploadLimitResolver` (see 6 above).
- **ModularityTests** pass in this worktree. The files-core report predicted two failures; I did not change `cv`, and did not check why they pass now.
- **Expiry test** uses `ticket-seconds=2` and a 2.1 s sleep. It is the only slow test in the set.
