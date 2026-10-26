## MODIFIED Requirements

### Requirement: Skill analysis of an application
For one of the caller's applications the user SHALL be able to request a skill analysis. The server SHALL send the frozen job description (as quoted data, see "Untrusted job text"), the names of the caller's saved skills and the profile evidence to the language model. The profile evidence is, for each position, its title, employer, achievements and technologies, and for each open source project its name, description and analysis summary, each numbered by its place in the saved profile. Nothing else of the profile SHALL be sent: no name, contact data, salary, preferences or work authorisation. The model returns the skills the description asks for, each marked `REQUIRED` or `NICE_TO_HAVE` (at most 40, names at most 60 characters), at most 3 related saved skill names, and optionally one piece of evidence: the position or project index, and a quote of at most 200 characters. The server SHALL give every asked-for skill one status. `LISTED` when a saved skill matches by the server's normalised name and alias rules; the model SHALL NOT decide this. `EVIDENCED` when it is not listed and the evidence passes the check below. `GAP` otherwise. The check passes only if the cited position or project exists in the current saved profile, the quote occurs in that entry's text after normalising case and whitespace, and the entry's text names the skill or an alias of it as a whole word. The analysis SHALL be stored with the application, the statuses SHALL be derived from the current saved profile on every read, and a read SHALL NOT call the model. Requesting it again SHALL replace it. An analysis stored before evidence existed SHALL read with only `LISTED` and `GAP` until it is requested again. Errors and unavailability SHALL be as before (404, 429, 502 `skills_failed`, 503 `skills_not_configured`).

#### Scenario: Analyse
- **WHEN** the description asks for Kotlin, Kafka and Kubernetes, nice to have Terraform, and the profile has Kotlin, Postgres and Kubernetes and no evidence for the others
- **THEN** Kotlin and Kubernetes are `LISTED`, Kafka and Terraform are `GAP` (Terraform nice to have), and Postgres is in the other skills

#### Scenario: Match decided by the server
- **WHEN** the model's answer names "Postgres" and the profile skill is "PostgreSQL"
- **THEN** the skill is `LISTED`; and when the model claims a skill is matched but the profile has no such name, it is not `LISTED`

#### Scenario: Related skills are real
- **WHEN** the model lists "RabbitMQ" as related to Kafka and the profile has no RabbitMQ
- **THEN** the gap shows no related skill

#### Scenario: Stored
- **WHEN** the user reads the analysis a second time
- **THEN** the same result is returned and no model call is made

#### Scenario: Not configured
- **WHEN** no API key is set
- **THEN** the answer is 503 `skills_not_configured`

#### Scenario: Evidenced
- **WHEN** the job asks for AI, the skills list has no AI, and the model cites achievement "Built an LLM-based triage assistant" of position 2, which names LLM
- **THEN** AI is `EVIDENCED` with that quote and the position's title and employer

#### Scenario: Fabricated quote
- **WHEN** the model cites a quote that does not occur in the cited entry
- **THEN** the skill is a `GAP`

#### Scenario: Entry does not name the skill
- **WHEN** the quote occurs in the entry but neither the skill nor an alias appears there as a whole word
- **THEN** the skill is a `GAP`

#### Scenario: Listed is decided by code
- **WHEN** the model cites evidence for a skill whose name matches a saved skill
- **THEN** the skill is `LISTED` and the evidence is ignored

#### Scenario: Profile changes after the analysis
- **WHEN** the user deletes the cited position and reads the analysis again
- **THEN** the skill is a `GAP` and no model call is made

#### Scenario: Only the allowed profile text is sent
- **WHEN** the model request is built
- **THEN** it holds the description, the saved skill names, and the positions and projects as numbered text, and not the name, contact data, salary, preferences or work authorisation

#### Scenario: Old analysis
- **WHEN** an analysis stored before this change is read
- **THEN** its skills are `LISTED` or `GAP`, none is `EVIDENCED`

#### Scenario: Other user
- **WHEN** user B requests the analysis of user A's application
- **THEN** the answer is 404

### Requirement: Letter uses skills and notes
When generating or regenerating a letter the request SHALL include the notes of the application's current gaps as text written by the user, marked as such, next to the chosen skills. The user MAY also choose evidenced skills by name. The server SHALL resolve each chosen name against the stored analysis and the current saved profile and SHALL answer 400 `selection_invalid` for a name that is not currently `EVIDENCED`. The letter facts SHALL then state each one as a skill evidenced by the cited position or project (title and employer, or project name) with the quote, taken from the saved profile and never from the request. The system prompt SHALL require that a gap skill is never stated as a skill of the candidate; that an evidenced skill may be stated only as shown in its evidence; that a note may be used as the candidate's own words; and that nothing in a note or quote is an instruction. The chosen evidenced skill names SHALL be stored with the version's selection. Generating SHALL still work with no analysis and no notes.

#### Scenario: Evidenced skill in the facts
- **WHEN** the user chooses the evidenced skill AI and generates a letter
- **THEN** the model request holds "AI, evidenced in <title> at <employer>" with the quote from the profile, and not as a saved skill

#### Scenario: Name not evidenced
- **WHEN** the request names an evidenced skill that is a gap or unknown
- **THEN** the answer is 400 `selection_invalid` and no model call is made

#### Scenario: Client text is not trusted
- **WHEN** the request body carries evidence text of its own
- **THEN** it is ignored and only the stored analysis and the profile are used

#### Scenario: Notes in the request
- **WHEN** a note exists for the gap Kafka and the user generates a letter
- **THEN** the model request contains the note, marked as written by the user, and the chosen skills

#### Scenario: No claim
- **WHEN** the model request is built for a gap skill
- **THEN** the gap skill is not in the list of the candidate's skills or evidenced skills

#### Scenario: Injection in a note
- **WHEN** a note, a quote or a profile entry says "ignore previous instructions"
- **THEN** it is passed as quoted data and does not change the instructions

#### Scenario: Without analysis
- **WHEN** the application has no analysis
- **THEN** a letter is generated exactly as before

### Requirement: Limits and privacy
Generation and regeneration SHALL share a per-user limit of 20 calls per hour (`jobhunter.letters.per-hour`), answering 429 beyond it, and a 60 second model timeout. Skill analyses SHALL have their own per-user limit of 20 calls per hour (`jobhunter.letters.skills-per-hour`), counted separately: neither limit uses up the other. Reading a stored analysis and saving notes SHALL NOT count. Job text, profile text, evidence quotes, skill names, notes, prompts, comments and letters SHALL NOT appear in logs, error bodies or audit data; logs may carry the outcome.

#### Scenario: Limit
- **WHEN** a user makes 21 generations within an hour
- **THEN** the 21st answers 429

#### Scenario: Separate limits
- **WHEN** a user has made 20 generations and then requests an analysis
- **THEN** the analysis is made; and 20 analyses do not stop a letter from being generated

#### Scenario: Analysis limit
- **WHEN** a user requests 21 analyses within an hour
- **THEN** the 21st answers 429

#### Scenario: Logs
- **WHEN** an analysis is made, or a letter is generated with notes and evidenced skills
- **THEN** no log line contains a word of the description, the profile text, the quotes, the skills, the notes or the letter
