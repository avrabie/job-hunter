# Design

## Context

- `JobExtractor.extract(pageText)` (llm) returns a `JobDraft`. `jobimport` calls it, then checks that at least 80 % of the description's words occur in the page text, stores a `job_import_draft` row, and on confirm hands a `JobData` to `applications.trackImported`. The user reviews and may edit title, company, location and description between the two.
- `application` has no address or contact columns, and `location` from the draft is checked but not kept. `ApplicationPatch` treats a null field as "leave as is".
- `ravProof` (already deployed, see the earlier commit) returns the position and the posting link, and `null` for the other five fields because nothing is stored.
- `llm/internal/ContactGrounding` keeps a CV contact value only when the CV text contains it. It is the pattern to follow.
- Modules: `jobimport` reaches `applications` only through its public API (`JobData`); `llm` is reached only through `JobExtractor`.

## Goals / Non-Goals

**Goals:**
- Fill the RAV fields from the ad with no extra LLM call, and make a wrong or invented value unlikely to reach a government form.
- Let the user see and fix every value before and after saving.

**Non-Goals:**
- A generic "company" entity shared between applications. The details live on the application, as a frozen copy like the rest of the job.
- Normalising addresses (street number split, canton lookup, country). Values are stored as written, and the postal code is only checked for shape.

## Decisions

**1. One extraction call, six more fields.** `JobDraft` gains `street`, `postalCode`, `city`, `contactPerson`, `contactPhone`, `contactEmail` (all `String?`, default `null`). The prompt gets the rules from the llm spec. Alternative: a second call that only looks for contacts. Rejected: doubles the cost and latency of an import for fields that are usually in the same text.

**2. Grounding lives in `jobimport`, not `llm`.** A new `JobContactGrounding` in `jobimport/internal` runs after the extractor, next to the existing description check, because that module owns the page text and the paste fallback. The `llm` module keeps returning "what the model said". The rules are the ones in the job-import spec (email, phone digits, postal code shape and occurrence, words of street, city and person present in the text). A dropped value becomes `null` and is not logged. Alternative: ground inside `KoogJobExtractor`. Rejected: the extractor would need the same text it was given and its tests would mix model output with text matching; a pure function over (draft, pageText) is easy to test, including a planted value.

**3. Six columns, not a JSON blob.** One Liquibase changeset (`014-application-contact.sql`, next number after the current last file) adds nullable `company_street`, `company_postal_code`, `company_city`, `contact_person`, `contact_phone`, `contact_email` to both `application` and `job_import_draft`, with `--rollback` dropping them. They are plain columns because they are read and patched one by one and never queried together. No defaults, so existing rows read `null` and the migration is instant.

**4. Carrying them through the existing types.** A small `ContactDetails` data class (the six `String?`) is added to `applications`' public API and used in `JobData`, `ApplicationDetail`, `ApplicationPatch` and `RavProof`'s source, and `jobimport` has its own copy in `ImportPreview`/`ImportEdits` (the draft's types are `jobimport` types and must not depend on `applications`' internals). `ApplicationSummary` is unchanged, so the list stays light.

**5. Clearing a field.** `ApplicationPatch` and `ImportEdits` keep "null means unchanged", so a blank string means "clear" and is stored as `null`. Alternative: a separate "clear" list or explicit JSON null handling. Rejected: the existing patch has no way to tell "absent" from "null" and a blank string is the simplest thing the UI can send.

**6. Validation is shared in meaning, not in code.** `applications` validates patches (lengths, email shape) and `jobimport` validates edits with the same limits, as it already does for title and company. Postal code is limited to 10 characters but not to digits on edit, so a foreign code can be typed; only the extraction grounding insists on 4 or 5 digits.

**7. RAV composition in one place.** `ravProof` builds `companyAddress` from the stored parts ("street, postal code city", skipping missing parts). The endpoint and the UI card do not change shape; the card gains a pointer to the section for filling missing values.

**8. UI.** The review step and the application page show the same six inputs through one small component. On the application page they are part of the existing save, so the page has one "Save" for notes and details. Alternative: a separate save for details. Rejected: two save buttons on one page invite lost edits.

## Risks / Trade-offs

- [The model picks a wrong contact that is on the page, such as a recruiter or a footer address] → the prompt excludes generic contacts, the user reviews every value in the import step, and the RAV card is copy-only.
- [Grounding is a text match, so a value planted in the page passes] → nothing is sent automatically, the user reviews the values, and the proposal records the residual risk. Hidden text is already stripped by the page-text extraction.
- [A phone or address written in a different way than the model returns, such as "Bahnhofstr." for "Bahnhofstrasse"] → the value is dropped and shown empty. A missing value is cheaper than a wrong one on a RAV form.
- [Third-party personal data is stored] → per-user scope (404 for another user), removal with the application and the account, no logging, no copy into shared postings.
- [A longer extraction answer] → a few dozen more output tokens per import; limits on the answer size already apply.

## Migration Plan

1. Deploy the backend with the changeset; the columns are nullable, so old and new pods can run side by side during the rollout.
2. Deploy the UI after the backend, because the UI reads the new fields and the old backend would not send them.
3. Rollback: redeploy the previous images; the extra columns are ignored. Dropping them is possible with the changeset's rollback and loses only the new details.
