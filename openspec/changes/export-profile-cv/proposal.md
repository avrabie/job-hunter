# Proposal

## Why

The profile now holds everything a CV needs (experience, education, skills, languages, publications, open source projects with analysis), but the only way out is the profile page itself. The user wants a CV document from it, as Markdown, HTML or PDF, in a few good-looking layouts, in English or German. The facts are already there; this change is the presentation layer.

## What Changes

- New `POST /api/cv/export`: renders the caller's **saved** profile as Markdown, standalone HTML or PDF (A4, selectable text) in one of three templates (Classic, Modern, Compact) and in English or German. Deterministic: no language model, no cost, nothing invented. Only content the profile holds is printed.
- Options choose the sections, whether open source projects appear (and whether their analysis summary is added), and whether the work authorisation is printed. Preferences (target roles, workload, salary, cantons) are never printed.
- The profile gains a **Contact** block (full name, headline, email, phone, website, LinkedIn), edited on the profile page. Linked code accounts (GitHub) print as links. A CV needs a name, so export refuses a profile without a full name.
- New "Export CV" page in the UI: pick format, template, language and sections, see a live HTML preview in a sandboxed frame, download the file.
- New dependency: `openhtmltopdf-pdfbox` 1.1.100 (HTML to PDF on the PDFBox 3.0.8 the backend already uses) and one bundled open font family for German and other European characters.

## Capabilities

### New Capabilities
- `cv-export`: the export endpoint, formats, templates, languages, options, escaping and failure rules.
- `ui-cv-export`: the export page with options, preview and download.

### Modified Capabilities
- `profile`: the Contact block.
- `ui-profile`: Contact fields on the profile page.

## Impact

- Modules: `cv` (new `export` package and controller, depends on the public `profile` API), `profile` (Contact), UI `profile` and a new `export` folder. No Liquibase change (JSON document, lenient reads).
- Personal data: the CV is generated from the caller's own saved profile and returned to them; nothing is stored or logged (no profile text in logs). Contact data (email, phone) is new personal data inside the profile document, covered by the existing cascade delete and export.
- Per-user scoping: the endpoint takes no user id and no profile in the request; it reads the caller's profile by `sub`.
- Safety: all profile text is escaped before it enters HTML; PDF rendering cannot fetch external resources.
- Cost: none from Claude. PDF rendering is CPU work, so a per-user hourly limit applies.

## Out of scope

Translating the user's own text (it is printed as written), photos, DOCX, saving the result into the file cabinet, tailoring a CV to a posting (that stays in the `applications` roadmap and will reuse these templates), motivation letters, custom user-defined templates, signing or encrypting PDFs.
