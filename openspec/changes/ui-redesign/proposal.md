## Why

The UI grew feature by feature on one global stylesheet (283 lines) and a 32 rem centred column. There is no navigation (the signed-in home is a grid of buttons), the landing page is a title and a Sign-in button that says nothing about what the product does, and the profile page is one very long form: the owner's skills alone run to a very long list. Every new page (job tracking, letters, import, public CV) made this worse.

## What Changes

- **Design system**: one set of design tokens (colour, type scale, spacing, radius, shadow, focus ring) in CSS custom properties, light and dark themes, and shared components (page header, card, button variants, form fields, badges and chips, tables/lists, empty states, alerts, collapsible section). Responsive from phone width up.
- **Top navigation bar** for the signed-in app: product name, links (Postings, Matches, Job tracking, CV, Profile, Admin for admins), the user's name/email with a Sign out action, a hamburger menu on phones, current-page highlight, skip-to-content link. The old home becomes a short overview page.
- **Landing page** for visitors: hero, capability cards (find and match Swiss jobs, track applications, motivation letters, CV export with photo, public CV page, import a job from a URL), a three-step "how it works", a privacy note (you review everything; nothing is sent to employers; your data is yours), Sign in and Register. English only. No call to the API.
- **Profile page**: sections become collapsible (accordions, native `details`/`summary`) with a one-line summary each and an "Expand all / Collapse all" control; **skills are auto-grouped** by technology type (same groups and keyword rules as the CV export) with each group collapsible, a count per group, and a filter box; the long list is scannable and the editing behaviour is unchanged.
- **All other pages** (postings, posting, matches, job tracking, application with letter workspace, import, export CV, public CV, admin, register) are moved onto the design system: same behaviour, new layout and styling.

## Out of scope

- Behaviour changes in any feature, new API endpoints, or backend changes (one shared test-case file for the skill groups is the only backend-side touch).
- German translation of the app (the landing page is English only).
- Illustrations or a logo beyond an inline SVG mark; no third-party UI library, icon font or web-font CDN (CSP allows only the own origin).
- Changing the CV and public-CV output templates (those are documents, not the app UI).

## Capabilities

### New Capabilities
- `ui-design-system`: tokens, themes, shared components, navigation, responsiveness, accessibility, CSP compliance.

### Modified Capabilities
- `ui-sign-in`: the landing page content and the signed-in shell (navigation and overview).
- `ui-profile`: collapsible sections and grouped, collapsible, filterable skills.

## Impact

- Code: `ui/` only (stylesheet split into tokens/base/components, new `Layout`, `NavBar`, `Landing`, `Collapsible`, `SkillGroups` modules, every page's markup), plus `docs`. One shared fixture `skillGroups.cases.json` is read by a UI test and by the backend `SkillGroupsTests` so the two keyword tables cannot drift.
- Personal data: none new; the landing page still shows no user data and calls no API; skill grouping happens in the browser on data already loaded.
- Security: the CSP (`style-src 'self'`, no inline styles, no inline scripts) stays; fonts are self-hosted files; `ui/test-image.sh` keeps passing.
- Cost/risk: large diff but mechanical after the first group; deployed in two steps so the owner can judge the look on prod early (the owner reviews on prod; no local browser checks).
