# files-core report (file-cabinet tasks 1.1, 1.2, 1.3, 2.1, 2.2)

## Tasks finished

- **1.1** `./gradlew test --tests '*FilesSchema*' --tests '*DatabaseBaseline*'` passed. FilesSchemaTests 4 of 4, DatabaseBaselineTests 3 of 3. Changeset count is now 11.
- **1.2** `./gradlew test --tests '*FileStorage*'` passed. FileStorageTests 5 of 5. Disk safety: `open` and `delete` accept only keys of the form `xx/<canonical uuid>` where `xx` equals the first two characters of the uuid. Anything else throws `IllegalArgumentException`, and the resolved path is also checked to stay under the root. The only path source is a generated UUID.
- **1.3** `./gradlew compileKotlin compileTestKotlin` succeeded. `./gradlew test --tests '*CvContent*'` passed, CvContentTests 7 of 7. `CvContent.kt` moved to `files/internal`, with `displayNameOnly` added. CvApiTests still compiles and passes.
- **2.1** `./gradlew test --tests '*FileRepositoryTests'` passed, 7 of 7. Covers scoping, ordering, kind filter, master switch, the OTHER-master rollback, clear, and delete returning the key once.
- **2.2** `./gradlew test --tests '*FileCabinet*'` passed, FileCabinetTests 2 of 2. Covers no master giving null through the Spring bean, and a master CV resolving to a readable path with the right bytes.

## Tasks not finished

None of the five assigned tasks is unfinished. Tasks 3.x and later were not started, as instructed.

## Files

Created:
- `backend/src/main/resources/db/changelog/008-files.sql`
- `backend/src/main/kotlin/net/s4v3/backend/files/FileCabinet.kt`
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FilesSettings.kt`
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FileStorage.kt`
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/CvContent.kt` (moved from `cv/internal`, plus `displayNameOnly`)
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/JdbcFileRepository.kt` (also holds `FileRecord` and `FileKind`)
- `backend/src/main/kotlin/net/s4v3/backend/files/internal/FileCabinetService.kt`
- `backend/src/test/kotlin/net/s4v3/backend/FilesSchemaTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/FileStorageTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/CvContentTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/FileRepositoryTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/FileCabinetTests.kt`

Changed:
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (include 008)
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvController.kt` (imports only)
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/JdbcCvStore.kt` (import only)
- `backend/src/test/kotlin/net/s4v3/backend/DatabaseBaselineTests.kt` (11 changesets, `user_file`)
- `openspec/changes/file-cabinet/tasks.md` (ticked 1.1, 1.2, 1.3, 2.1, 2.2)

Deleted: `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvContent.kt` (moved).

## Surprising, needs a decision

1. **ModularityTests fails after my changes, as expected.** Two tests fail in `ModularityTests`. (a) "module structure is valid": `cv` (CvController, via the import task 1.3 requires) depends on `files.internal.CvContent`, `CvRejected` and `CvRejection`, which Spring Modulith reports as non-exposed. This goes away when task 6.2 deletes CvController. The alternative is to expose these types from the `files` package, which the spec does not ask for, so I did not do it. (b) "module set is exactly the ten planned modules": it now finds eleven, including `files`. This is task 6.2's job. Neither test was edited, because `ModularityTests.kt` is not in my task list. Lead decision: accept the red `ModularityTests` until 6.2, or let me change it now.
2. The other 62 tests in the combined run (`*Cv*`, `*FileCabinet*`, `*FileRepository*`, `*FileStorage*`, `*FilesSchema*`, `*DatabaseBaseline*`, `*CvContent*`, `*Modularity*`) pass. Only the two ModularityTests fail.
3. `FileStorage` takes `FilesSettings` in its constructor, not a `Path`, so Spring can construct it. Tests build it as `FileStorage(FilesSettings(dir = ...))`. `moveIn` deletes the temp file if reading or moving fails. `FileStorage` does not create the root or `tmp` directories yet; task 3.1 says to do that on start.
4. `FileCabinetTests` builds `FileCabinetService` by hand against a `@TempDir` storage, because the Spring default `/data/files` cannot be written in tests. The Spring bean is still autowired and used for the no-master case.
5. `FileRepositoryTests` and `FileCabinetTests` are `internal` classes, because they expose internal types in their constructors.
6. `setMaster` uses `TransactionTemplate`, not `@Transactional`, and returns false (rolling back the clear) when the file is not a CV of that user.
