## ADDED Requirements

### Requirement: Interested action
Each posting in the list and on the posting page SHALL have an "Interested" action for signed-in users. It tracks the posting and then shows the current stage with a link to the application. A posting already tracked SHALL show its stage from the start. A failure SHALL show a message and leave the posting as it was.

#### Scenario: Mark interested
- **WHEN** the user presses Interested
- **THEN** the posting shows the stage Interested and a link to the application
