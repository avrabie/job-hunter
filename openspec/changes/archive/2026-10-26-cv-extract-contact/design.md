# Design

## Context

`CvReader.sectionFrom(text, ProfileSection)` runs one structured request per section; `DraftMapper` turns the answer into a `Profile`. The contact block exists in `Profile.Contact` and is validated on save (`ProfileValidator`: https links, email, phone), but no extraction produces it today. The UI replaces list sections wholesale after a confirm; `ProfilePage.onFillSection` handles list sections only.

## Decisions

1. **Same endpoint, new section.** Add `CONTACT` to `ProfileSection` and a `ContactSection` draft (`fullName`, `headline`, `email`, `phone`, `website`, `linkedin`, all nullable strings) in `SectionDrafts.kt`. The prompt adds: "Use the candidate's own details from the CV header or footer; never use a referee's or a company's contact; leave a field null if absent." The controller's allowed names become experience, education, publications, contact. Limits and errors are unchanged, and the call counts once for the hourly limit.
2. **Grounding in the backend, not trust in the model.** After the draft is mapped, `ContactGrounding` keeps a value only if it occurs in the CV text: email case-insensitive exact; phone when its digits (leading `+`/`00` ignored) are a contiguous run in the CV's digits; website and LinkedIn when host and path occur in the text after removing scheme, `www.` and a trailing slash; full name when each word occurs (case-insensitive); headline is not grounded (it is a free sentence) but is limited to the existing length rule. Values that fail are dropped silently (null), same as the job import grounding. This also removes any value from prompt-injected text that is not in the CV.
3. **Normalise through the same rules as saving.** Links must be absolute `https` (a bare `linkedin.com/in/ada-example` gets `https://`), email and phone must pass the rules used by `ProfileValidator`; failures are dropped. The draft never reaches the user in a shape the server would refuse on Save.
4. **Whole-profile extraction unchanged.** It is not used by the page any more; adding contact there is out of scope.
5. **UI fill rule (assumption).** The Contact "Fill from CV" button requests `section=contact`, then, per field: empty form field and non-empty draft value → fill. Non-empty form field with a different draft value → collected; if any, `window.confirm` lists the field labels (not the values, which are personal data in a dialog and in tests' output) and replaces them only on OK. Equal values are ignored. Result is unsaved, with the existing notice. If the draft is empty the page says nothing was found.
6. **Logging.** No CV text, model output or contact value in any log; only outcome, size, duration (existing rule in `cv`).

## Risks

- A CV that writes the email as `ada [at] example.com` or hides the phone in an image is not found; the field stays empty. Acceptable.
- Grounding drops a correct phone written with letters or unusual separators; acceptable, the user can type it.

## Open for the owner

- Fill only empty fields and ask before overwriting (chosen default), versus always replace like the list sections.
