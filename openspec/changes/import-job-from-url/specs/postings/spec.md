## ADDED Requirements

### Requirement: User-added source
The postings read model SHALL expose `userAdded`, true when the posting's primary source is `user-import`.

#### Scenario: Marker
- **WHEN** a posting was created by an import
- **THEN** list and detail responses have `userAdded=true`, and crawled postings have `userAdded=false`

#### Scenario: Crawled posting found by import
- **WHEN** an import matches a posting a crawler created
- **THEN** its primary source stays the crawler and `userAdded` stays false
