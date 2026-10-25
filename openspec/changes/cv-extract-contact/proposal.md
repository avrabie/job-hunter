# Proposal

## Why

The profile page can fill Experience, Education and Publications from the master CV, but the Contact section (full name, email, phone, links) has to be typed by hand even though every CV states these. The full name is also required to export a CV, so a new user is blocked until they type it.

## What Changes

- `POST /api/cv/extract?section=contact` reads the contact block from the master CV and returns it as a draft, like the other sections: nothing is stored, same limits, same errors.
- Contact values are checked against the CV text before they are returned: an email, phone number, link or name that the CV does not contain is dropped, so the model cannot invent one.
- The Contact section on the profile page gets a "Fill from CV" button. The result goes into the form as unsaved changes. Assumption for the owner: only **empty** fields are filled; if some filled fields would change, the page asks first and lists them.
- No new tables, no profile format change. The whole-profile extraction is unchanged.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `cv`: "Extract a single section" accepts `contact`; new requirement that contact values must appear in the CV text.
- `ui-profile`: new requirement "Fill the contact section from the CV".

## Impact

- Backend: `llm` (`ProfileSection.CONTACT`, a contact draft type, prompt, grounding), `cv/internal/CvExtractController` (accepts `contact`). No schema change.
- UI: `ui/src/profile` (`analysisApi.ts` section type, `ProfilePage.tsx` fill logic, `ContactSection.tsx` button), tests.
- Privacy: the same CV text already goes to the model for the other sections; nothing new is stored or logged.
