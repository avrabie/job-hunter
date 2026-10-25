## 0. Lead checks

- [ ] 0.1 Confirm with the owner after deploy A (design system, nav, landing, profile) that the look is right before converting the other pages.
- [ ] 0.2 Decide if a self-hosted variable font is used or the system font stack stays (size under 150 KB).

## 1. Design system and layout (Opus; everything else waits for this)

- [x] 1.1 `ui/src/styles/{tokens,base,components}.css` with tokens, light/dark themes, base typography, focus ring, and component styles; remove duplicated rules from `index.css` only when no page needs them yet (keep it importing the new files).
- [x] 1.2 Shared components (`Collapsible` with Expand/Collapse all support, `Card`, `PageHeader`, `Badge`, `Alert`, `EmptyState`, form field wrappers) with tests (keyboard, aria, error-opens-section).
- [x] 1.3 `Layout`, `NavBar` (hamburger, aria-current, skip link, admin role, sign out, identity), `Footer` (service status); route wiring in `App.tsx` for signed-in and signed-out layouts; tests.
- [x] 1.4 Build checks: no inline style in the built output, CSP-safe fonts, `ui/test-image.sh` passes; a contrast test of the token pairs.

## 2. Landing and home (after 1)

- [x] 2.1 New `Landing` (hero, capability cards from a data file, how it works, privacy, CTA) with tests (no API call, buttons, content).
- [x] 2.2 New signed-in `Home` overview (cards, counts per stage from the existing applications list, service status moved to the footer); tests.

## 3. Profile page (after 1)

- [x] 3.1 `skillGroups.ts` + shared `skillGroups.cases.json`, UI test, and the backend `SkillGroupsTests` reading the same fixture.
- [x] 3.2 Profile sections as `Collapsible` with summaries and Expand/Collapse all; error-opens-section and focus; unsaved-edits behaviour kept.
- [x] 3.3 Grouped, collapsible, filterable Skills section (start collapsed over 20 skills); the existing 1 578-line profile test stays green or is adapted only where wrappers moved.

## 4. Convert the remaining pages (after the owner approves deploy A; Haiku, one page per agent, in parallel)

- [x] 4.1 Postings list and posting page.
- [ ] 4.2 Matches.
- [ ] 4.3 Job tracking list, import form, application page and letter workspace.
- [ ] 4.4 Export CV and Public CV pages.
- [ ] 4.5 Admin and Register/Callback/error pages.
- [ ] 4.6 Delete the unused legacy rules from `index.css`.

## 5. Wrap-up

- [ ] 5.1 Full UI checks (vitest, tsc, oxlint --deny-warnings, prettier), backend `SkillGroupsTests`, `openspec validate --all`, image script; independent accessibility and CSP review (landmarks, one h1, keyboard path through nav and collapsibles, contrast, no inline styles).
- [ ] 5.2 Docs (`ui/src/README.md`, `docs/architecture.md`, glossary: "design tokens", "collapsible section"); deploy A and B only after the owner says so; archive after the owner tests.
