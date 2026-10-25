## ADDED Requirements

### Requirement: Design tokens and themes
The UI SHALL define its colours, type scale, spacing, radius, shadow and focus ring once as CSS custom properties, with a light and a dark theme following the system preference, and every component SHALL use only those tokens.

#### Scenario: Dark mode
- **WHEN** the system prefers a dark colour scheme
- **THEN** all pages use the dark token values and body text keeps a contrast ratio of at least 4.5:1 against its background

#### Scenario: One source
- **WHEN** the stylesheets are searched for raw colour values outside `tokens.css`
- **THEN** none are found

### Requirement: Content security policy compliance
The UI SHALL work under `style-src 'self'` and `script-src 'self'`: no inline styles, no `style` attributes, no inline scripts, no external fonts or stylesheets.

#### Scenario: Built output
- **WHEN** the production build is inspected
- **THEN** the HTML has no inline `<style>` or `style=` and all fonts are files from the same origin

#### Scenario: Image checks
- **WHEN** `ui/test-image.sh` runs
- **THEN** it passes unchanged

### Requirement: Main navigation
Signed-in pages SHALL show a top bar with the product name, links to Postings, Matches, Job tracking, CV (Export and Public page) and Profile, an Admin link only for administrators, the user's email or name, and Sign out.

#### Scenario: Current page
- **WHEN** the user is on `/applications/123`
- **THEN** the "Job tracking" link has `aria-current="page"`

#### Scenario: Admin link
- **WHEN** the user has no ADMIN role
- **THEN** no Admin link is rendered

#### Scenario: Phone width
- **WHEN** the viewport is narrower than 48 rem
- **THEN** the links collapse behind a button with `aria-expanded`, that opens and closes the menu, closes on Escape and on navigation, and returns focus to the button

#### Scenario: Skip link
- **WHEN** a keyboard user presses Tab on a page
- **THEN** the first focus stop is "Skip to content", which moves focus to the main content

### Requirement: Shared components
The UI SHALL provide shared page header, card, button variants, form field, badge, empty state, alert and collapsible-section components, and every page SHALL use them instead of page-specific styling.

#### Scenario: Consistent headings
- **WHEN** any page is rendered
- **THEN** it has exactly one `h1` inside `main`, and its landmarks are header, nav, main and footer

#### Scenario: Behaviour unchanged
- **WHEN** the existing feature tests run after a page is converted
- **THEN** they pass with the same accessible names, roles and labels

### Requirement: Responsive layout
Every page SHALL be usable from 360 px wide, without horizontal page scrolling, with touch targets of at least 40 px.

#### Scenario: Narrow viewport
- **WHEN** a page is rendered at 360 px
- **THEN** its tables or long rows scroll inside their own container and nothing overflows the page

### Requirement: Collapsible sections
The UI SHALL offer a collapsible section built on `details` and `summary`, with a one-line summary, keyboard operation, and an optional "Expand all / Collapse all" control for groups of sections.

#### Scenario: Keyboard
- **WHEN** the summary has focus and the user presses Enter or Space
- **THEN** the section toggles

#### Scenario: Error opens the section
- **WHEN** a field inside a closed section fails validation
- **THEN** the section opens and the invalid field receives focus
