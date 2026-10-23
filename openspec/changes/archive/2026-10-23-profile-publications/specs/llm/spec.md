# Spec Delta

## ADDED Requirements

### Requirement: Publications in the draft
The typed CV draft SHALL include publications taken from the CV's publications section, each with title, and venue, year, authors and link only when the CV states them. A publication without a title SHALL be left out. A year outside 1900 to the current year plus one, and a link that is not an `http` or `https` address, SHALL become null while the rest of the publication is kept.

#### Scenario: Publications read
- **WHEN** the model returns two publications with titles, one with a year and a link
- **THEN** the draft holds both in order with the stated fields and null for the others

#### Scenario: Bad values dropped
- **WHEN** the model returns a publication with year 3020 and url `ftp://example.org/x`
- **THEN** the publication is kept with null year and null url

#### Scenario: No title
- **WHEN** the model returns a publication with a blank title
- **THEN** that publication is left out and the others are kept
