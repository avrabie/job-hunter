# Baseline fix report

- Changed: backend/src/test/kotlin/net/s4v3/backend/DatabaseBaselineTests.kt only.
- Count expectation raised 11 -> 15, with a one-line comment: raise by one per new changeset.
- Added assertion that public_cv, public_cv_handle_hold and public_cv_audit exist.
- Other tests checked: 'running the changelog again' compares before/after snapshots (no hard-coded count); vector test is independent. No other changes needed.
- Verify: ./gradlew integrationTest --tests "DatabaseBaselineTests" (run in backend/): BUILD SUCCESSFUL; 3 tests, 0 failures, 0 errors, 0 skipped.
- Not committed.
