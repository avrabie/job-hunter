## ADDED Requirements

### Requirement: Skills for this job
Before the generate button the letter workspace SHALL show a step "Skills for this job". Without an analysis it SHALL show a button "Analyse the job" and a one-line explanation. With one it SHALL show three lists: "Advised to include" (skills the job asks for and the profile has, marked required or nice to have, ticked by default), "Gaps" (skills the job asks for and the profile lacks, marked required or nice to have, each showing related skills of the user when there are any, and a note field of at most 500 characters with a counter) and "Your other skills" (unticked). The ticked skills SHALL be the skills sent with the letter, in place of the old skill choice. The user SHALL be able to tick and untick any skill and to repeat the analysis. Notes SHALL be saved with a button and shown again on the next visit; saving SHALL NOT change the profile and the page SHALL say so. The step SHALL show fixed messages for 429, 502 and 503 and SHALL stay optional: the user can generate a letter without analysing. All text is rendered as text, never as HTML.

#### Scenario: Analyse and generate
- **WHEN** the user presses "Analyse the job", writes a note for a gap and presses Generate
- **THEN** the ticked skills and the note are sent with the letter request

#### Scenario: Defaults
- **WHEN** an analysis is shown
- **THEN** the skills in "Advised to include" are ticked and the skills in "Your other skills" are not

#### Scenario: Notes kept
- **WHEN** the user saves a note and reopens the page
- **THEN** the note is in the field

#### Scenario: Profile not changed
- **WHEN** the user saves notes
- **THEN** no profile request is sent and the page says the notes stay with this application

#### Scenario: Not configured
- **WHEN** the backend answers 503
- **THEN** the step says the analysis is not available and the rest of the workspace still works

#### Scenario: No analysis
- **WHEN** the user skips the step
- **THEN** the old skill choice is available and a letter can be generated
