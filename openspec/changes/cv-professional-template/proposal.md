# Proposal

## Why

The owner has a CV they like (`private/sample/cv-sample.pdf`, git-ignored: it is real personal data and must never be copied into the repository). It has a navy and orange look, a round photo at the top right, a grouped skills table and an experience layout with dates on the right. The three existing templates do not look like it, and no template can show a photo. This change adds a fourth template, **Professional**, that reproduces that look from any user's profile, and lets a user put a photo from their file cabinet on it, in the export and (owner's decision) on the public CV page.

## What Changes

- **New template `PROFESSIONAL`** for HTML and PDF (Markdown has one structure for all templates, unchanged, and has no photo): navy uppercase name, orange headline, contact lines under the name, round photo at the top right, letter-spaced navy section headings with a rule, justified summary, skills as a two-column table (group on the left, items on the right), experience entries with the title in navy bold on the left and the dates on the right, the company in orange with the place right-aligned in italics, an italic context line, square bullets with the lead-in word in bold, and a footer line `Name · Headline` on every page (PDF).
- **Auto-grouped skills (owner's decision).** The Professional template groups skills by technology type (Programming languages, Frameworks, Databases, Messaging, Cloud and containers, Testing, Security, Tools, Other) with a built-in keyword list; unknown skills go under "Other". No profile change. Other templates keep the flat list.
- **Photo.** The user picks any `.jpg`/`.jpeg` file from their file cabinet. The server re-encodes it (square centre crop, 320 px, round mask on white, EXIF and all metadata dropped), so the original is never exposed and a photo of any size is safe. The photo is optional, off by default, and printed only on the Professional template.
- **Export.** The export request gets an optional `photoFileId`. The export page gets a template choice "Professional" and a photo picker listing the user's JPEG files.
- **Public page.** The public CV settings get an optional `photoFileId` (off by default, like every field). The public page serves the processed photo from `GET /api/public/cv/{handle}/photo` (no sign-in, same uniform 404, headers and limits as the page), embedded in the page itself as a data URI so the page stays one self-contained document. The Public CV page of the UI gets the template choice and the photo picker, with a visible note that the photo is public.
- **File cabinet.** A new public API lets other modules read a user's own image file by id (`FileCabinet.image(sub, id)`), for JPEG files only; the files module stays the only code that knows storage.

## Capabilities

### Modified Capabilities
- `cv-export`: the Professional template, grouped skills, the photo option.
- `file-cabinet`: read one of the user's JPEG files for another module.
- `cv-public`: photo setting and route; Professional template allowed.
- `ui-cv-export`: template choice and photo picker.
- `ui-cv-public`: template choice and photo picker with a public-photo warning.

## Impact

- Backend: `cv.export` (new template, `CvPhoto`, `SkillGroups`, `PhotoProcessor`), `cv.publiccv` (setting, photo route, cache key), `files` (`FileCabinet.image`), `SecurityConfig` unchanged (`/api/public/cv/**` is already open).
- UI: `ui/src/export`, `ui/src/publiccv`.
- No database migration: public settings are a JSON document and the new field defaults to null.
- No new dependency: JPEG decoding and encoding use `javax.imageio` from the JDK, which the PDF renderer already relies on.
- Privacy: the photo is personal data. It is never logged, never in an error message, never in the audit trail (the audit flag only says `photo` is on).
