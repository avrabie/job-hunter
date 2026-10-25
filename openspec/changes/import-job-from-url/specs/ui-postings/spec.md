## ADDED Requirements

### Requirement: Added-by-a-user badge
The postings list and detail page SHALL show an "Added by a user" badge when `userAdded` is true.

#### Scenario: Badge
- **WHEN** a posting has `userAdded=true`
- **THEN** the badge is shown on its row and on its page, and not for crawled postings
