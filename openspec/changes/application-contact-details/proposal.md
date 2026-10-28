# Proposal

## Why

The RAV job-search proof needs the hiring company's address and postal code and the application contact (person, phone, email). Today an application stores none of them, so `GET /api/applications/{id}/rav` can only fill the position and the posting link; the other five fields are always `null`. The job ad usually states most of them, and the import already sends the ad's text to the LLM, so we can read them there and let the user correct them.

## What Changes

- `JobExtractor` also returns, when the page states them: street, postal code, city, contact person, contact phone, contact email. Every value is checked against the page text before it is kept; a value the page does not contain becomes `null` (the same idea as `ContactGrounding` for CVs).
- The import draft carries the six values; the review step shows them as editable fields and the confirm applies the user's edits.
- An application stores the six values as nullable fields, shows them in an editable "Company & contact" section on `/applications/:id`, and changes them through the existing `PATCH`. A blank value clears a field.
- `ravProof` reads the stored values: `companyAddress` is built from the street, postal code and city that are present, and `postalCode`, `contactPerson`, `contactPhone`, `contactEmail` come straight from the stored fields. Only fields that are truly missing stay `null`.
- **Not breaking:** all new fields are optional; existing applications keep working and show empty fields.

### Out of scope

- Re-scanning an existing application to fill its fields (the user types them in, or re-imports).
- Extracting from crawled-source postings (SwissDevJobs and others carry only city and canton); a follow-up.
- Sending anything to the RAV or an employer. The user still copies the fields by hand.
- Looking addresses or contacts up on the web, or from a company register.
- Putting the contact into the motivation letter.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm`: `JobExtractor`'s draft gains the six optional fields, with prompt rules that forbid inventing them and that the contact is the person named for applications, not a generic footer.
- `job-import`: the draft, the review edits and the confirm carry the six fields; every value is grounded against the page text and dropped when it is not found; none is logged.
- `applications`: an application stores the six fields, a patch can change or clear them, and the RAV proof (not yet specified) is specified, including how the address is composed.
- `ui-job-import`: the review step shows the six fields, editable.
- `ui-applications`: the application page has an editable "Company & contact" section, and the RAV card shows the stored values.

## Impact

- **Modules:** `llm` (extractor, prompt), `jobimport` (draft, grounding, confirm), `applications` (columns, patch, `ravProof`), UI `applications`. The `jobimport` → `applications` call stays through `JobData`, an `applications` public type; no new module dependency, so `ApplicationModules.verify()` is unaffected.
- **Database:** one Liquibase changeset adds six nullable columns to `application` and to `job_import_draft`.
- **API:** `ImportPreview`, `ImportEdits`, `ApplicationDetail` and `ApplicationPatch` gain optional fields; `PATCH /api/applications/{id}` accepts them.
- **Personal data:** a contact person's name, phone and email are personal data of a third party, taken from a public ad. They are stored only on the importing user's application, shown only to that user (another user's id still answers 404), deleted with the application or the account, and never logged. They are not copied into the shared postings.
- **Claude cost:** no extra call. The same extraction call returns six more short fields, so output grows by a few dozen tokens per import.
- **Prompt injection:** the page text is untrusted. A value planted on the page passes the grounding check, so the user reviews every value before confirming, and nothing is sent anywhere automatically.
