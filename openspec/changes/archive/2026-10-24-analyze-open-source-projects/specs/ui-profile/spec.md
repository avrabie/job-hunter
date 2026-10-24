## ADDED Requirements

### Requirement: Analyze a picked project
Each picked open source project SHALL have an "Analyze" button, enabled only for a project that has been saved (present in the last loaded or saved profile), with a hint otherwise. It SHALL call `POST /api/profile/open-source-projects/analyze`, show a loading state on that project, and on success put the analysis on the project and update the skills as unsaved changes, with a notice that nothing is saved until Save. The page SHALL show the summary, the architecture and the tech stack (name, category, evidence) of a project that has an analysis. It SHALL show a fixed message for each failure (`project_not_found`, `repository_not_found`, `too_many_analyses`, `provider_rate_limited`, `provider_unavailable`, `analysis_failed`, `analysis_not_configured`) and leave the project and the skills unchanged. When the project already has an analysis the page SHALL ask for confirmation before replacing it.

#### Scenario: Analyze
- **WHEN** the user presses Analyze on a saved project and the server answers 200
- **THEN** the analysis is shown on that project, the skills change as described below, the page says nothing is saved yet, and no profile request is sent

#### Scenario: Unsaved project
- **WHEN** a project was picked but not saved
- **THEN** its Analyze button is disabled with a hint to save first

#### Scenario: Failure
- **WHEN** the server answers 429 `too_many_analyses`
- **THEN** a fixed message appears at that project and nothing else changes

#### Scenario: Re-analysis
- **WHEN** the project already has an analysis and the user presses Analyze
- **THEN** a confirmation asks first, and declining changes nothing

### Requirement: Traceable skills from analysis
After a successful analysis the page SHALL add each technology to Skills: a skill with the same name (any letter case) gets the repository as a source and keeps its origin and level; otherwise a new skill with origin `ANALYSIS`, no level and that source is added. A source SHALL carry the repository's name and address and the evidence. The earlier sources of the same repository SHALL be removed first, and an `ANALYSIS` skill left without sources SHALL be removed. Removing a project, or its analysis, SHALL remove its sources the same way. The Skills section SHALL show an `ANALYSIS` skill as "from repository analysis" and list its sources as links with the evidence, and the user SHALL be able to remove any skill or source. The page SHALL NOT set a skill level.

#### Scenario: New skill
- **WHEN** the analysis names `Spring Boot` and the user has no such skill
- **THEN** a skill `Spring Boot` with origin `ANALYSIS`, no level and one source is added

#### Scenario: Existing skill
- **WHEN** the user already has the skill `kotlin` with level `EXPERT` and the analysis names `Kotlin`
- **THEN** that skill gets the source, keeps level `EXPERT` and origin `MANUAL`, and no second skill is added

#### Scenario: Re-analysis replaces sources
- **WHEN** a repository is analysed again and the new result no longer names `Maven`
- **THEN** its old source is removed from `Maven`, and that skill is removed if it was added by analysis and has no other source

#### Scenario: Removing a project
- **WHEN** the user removes a picked project
- **THEN** that repository's sources disappear from the skills, and skills added only by it are removed

#### Scenario: Traceable
- **WHEN** the user looks at a skill added by analysis
- **THEN** it shows the repository link and the evidence for it, and can be removed

#### Scenario: Save keeps them
- **WHEN** the user saves
- **THEN** the request holds the analysis on the project and the skills with origin and sources
