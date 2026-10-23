# cv-master report (file-cabinet tasks 6.1 and 6.2)

## Tasks finished

- **6.1** `./gradlew test --tests '*CvExtract*'` passed: CvExtractApiTests 11 of 11, 0 failures. Covers draft returned and profile untouched, reads the master (not another CV), 409 with no master (no files, and only non-master CV files), scanned PDF 422, rate limit 429, model failure 502 (fixed body) and missing key 503, PDF text read, per-user isolation, and the log-capture test (passes).
- **6.2** `cd backend && ./gradlew build` BUILD SUCCESSFUL. 355 tests, 0 failures, 0 errors, 0 skipped (71 result files). ModularityTests 2 of 2, KoogBoundaryTests 2 of 2, CvExtractApiTests 11 of 11. The count is 364 in files-api's report minus 11 from the deleted CvApiTests plus 2 new extraction tests.

Tasks not finished: none of the assigned tasks.

## Files

Changed:
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvExtractController.kt` (uses `FileCabinet.masterCv`, 409 when none, reads `CabinetFile.path` with a 5 MB guard; now also holds `cvError`, moved from the deleted CvController)
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvText.kt` (`of(bytes, contentType)`)
- `backend/src/test/kotlin/net/s4v3/backend/CvExtractApiTests.kt` (uploads and master through `/api/files`; two new scenarios; log test now deletes through `/api/files/{id}`)
- `openspec/changes/file-cabinet/tasks.md` (ticked 6.1 and 6.2)

Deleted:
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvController.kt` (also held `CvMessages` and `CvUploadErrors`)
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/JdbcCvStore.kt`
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvContent.kt` (the duplicate; `files/internal/CvContent.kt` remains)
- `backend/src/test/kotlin/net/s4v3/backend/CvApiTests.kt`

Not touched: `files/`, `db/changelog/` (table `cv_document` and `CvSchemaTests` kept), `ui/`, `k8s/`, `ModularityTests.kt` (see below). Nothing committed.

## Deviations and doubts

1. **The 409 body uses the `cv` error code.** It is `{"error":"invalid_cv","message":"Choose a master CV in your files first."}`, the same shape as the other extraction errors. The Contract's `invalid_file` is for the cabinet endpoints. Tell me if the extraction errors should change code; that would also touch 422, 429 and 502.
2. **`cvError` moved into `CvExtractController.kt`** (it is still used there). `CvMessages` was used only by the deleted controller, so it is gone.
3. **`MAX_CV_BYTES` is a private constant** (5 MB) in `CvExtractController.kt`, not imported from `files.internal.CvContent`, because `cv` cannot see `files.internal`. It is a guard only: a master of kind CV is always at most 5 MB. A file over the guard gives 422.
4. **`ModularityTests` needed no edit.** It already lists the eleven modules including `files`, and both tests pass. I did not change it.
5. **`CvInfo.kt` (package `net.s4v3.backend.cv`) is left in place.** Its only users were the deleted controller and store, so it is now unused. The task did not name it, so I did not delete it. Decision for you: delete it in a cleanup, or keep it.
6. **`CvExtractApiTests` runs in its own context** with `MockIssuerInitializer` and `FilesVolumeInitializer` (a temporary files volume), as `FilesApiTests` does. Without that, uploads would write to `/data/files`.
7. **The Tomcat 413 handler for the old endpoint is gone.** `CvUploadErrors` was the `@RestControllerAdvice` for `MaxUploadSizeExceededException` on all paths. Its removal means that outside `/api/files` (where `FilesController` has its own resolver, per the files-api report), a multipart size error now gets Spring's default answer. No endpoint that used it exists any more.
8. **The `cv_document` table is still used by `CvSchemaTests`** and by the mover that the other agent is writing. I did not drop anything.
9. **Build artifacts:** the Gradle build log was written to the scratchpad, not the repo. `backend/build/` is the normal gitignored build output.

## Decisions I need from the lead

- Items 1 and 5 above.
- Whether the new 409 message wording is acceptable (the spec gives no fixed text for it).
