# Proposal

## Why

A user can export a CV file, but cannot share a link. The owner wants every user to be able to choose a handle (a nickname such as `ada-example`) and have a public web page at `https://s4v3.net/cv/ada-example` that shows their CV, built from their profile. The profile already holds the content and the renderers already exist; this change adds the public address, the control over what is public, and the unauthenticated serving.

## What Changes

- **Handle.** A user claims one unique handle (lowercase letters, digits and single hyphens, 3 to 30 characters). Reserved words are refused. A handle can be changed at most once every 30 days; a released handle is held for 90 days so nobody else can take over an old link.
- **Public page.** `GET /api/public/cv/{handle}` (no sign-in) answers with the CV as a standalone HTML page, **rendered live from the owner's current profile**, so every saved profile change shows at once in the fields the owner made public. The pretty address `https://s4v3.net/cv/{handle}` is an edge route that rewrites to that endpoint, so visitors get the real server-rendered page with its own security headers. The direct address `/api/public/cv/{handle}` always works and is the fallback.
- **Per-field visibility, everything off by default (owner's decision).** The owner ticks what is public: each section, analysis summaries, the headline, each contact field (email, phone, website, LinkedIn), the code-account links, the location and the work authorisation. Nothing is public until ticked, except the full name, which the page cannot do without. Preferences (target roles, workload, cantons, salary) are never public, not even as options.
- **Owner controls.** A new "Public CV" page: handle, visibility ticks, template (Classic, Modern, Compact) and language, a preview of exactly what visitors see (opens in a new tab through a short-lived ticket), Publish, Pause (the address answers 404, settings kept), Remove (settings deleted), copy link, and a banner that says the page is live.
- **Safety.** Unknown, paused and removed handles all answer the same 404. Search engines are told not to index by default (opt-in). Per-IP and per-handle rate limits. A strict content security policy with `sandbox`. No profile text or handle in logs.
- **Takedown.** An administrator can unpublish any handle (`/api/admin/public-cv/{handle}`), for abuse or legal requests.
- The CV builder gets a contact-visibility option so the same code serves export and the public page; export behaviour is unchanged.

## Capabilities

### New Capabilities
- `cv-public`: handles, publishing and visibility, the public page, headers, limits, takedown, deletion with the account.
- `ui-cv-public`: the Public CV settings page and the public viewer route.

### Modified Capabilities
- `cv-export`: the builder accepts which contact fields to print (default all, so export is unchanged).

## Impact

- Modules: `cv` (new `cv.publiccv` package: service, controllers, repository, limiter, cache; `cv.export` builder option), `accounts` (one `permitAll` GET matcher in `SecurityConfig`; deletion cascades through the database), `web` unchanged; UI new folder `ui/src/publiccv/`; edge (`k8s/base`): one HTTPRoute rule for `/cv/`.
- Database: new Liquibase changelog `009-public-cv.sql` (table `public_cv`, `public_cv_handle_hold`, `public_cv_audit`, all `ON DELETE CASCADE` from `app_user`).
- Personal data: contact details and the CV become publicly readable, on the owner's explicit choice per field. Handles are public identifiers. The profile document is not copied: the page is rendered from the live profile, so account deletion removes it at once.
- Security: the first unauthenticated endpoint that returns user content. Threat model in `design.md`.
- Cost: none from Claude. CPU for rendering is bounded by caching and rate limits.

## Out of scope

Snapshot publishing (owner chose live), a public directory or search of CVs, view counters or visitor analytics, a contact form, PDF download and Open Graph cards (a follow-up change `public-cv-extras`), custom domains, embedding in other sites, a public photo, several public CVs per user, Open Graph and other social-preview tags (the server-rendered page makes them possible later).
