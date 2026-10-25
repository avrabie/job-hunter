## ADDED Requirements

### Requirement: Collapsible profile sections
The profile page SHALL show each section as a collapsible section with a one-line summary (for example the number of entries) and "Expand all" and "Collapse all" controls, without changing what is saved.

#### Scenario: Collapse and expand
- **WHEN** the user collapses Experience and saves
- **THEN** the saved profile is identical to saving it expanded

#### Scenario: Validation error
- **WHEN** a save fails because a field in a collapsed section is invalid
- **THEN** that section opens and the first invalid field has focus

#### Scenario: Unsaved edits
- **WHEN** the user edits a field, collapses its section and navigates away
- **THEN** the unsaved-edits warning still appears

### Requirement: Grouped skills
The Skills section SHALL group skills by technology type with the same groups and whole-token keyword rule as the CV export (languages, frameworks, messaging, security, cloud, testing, databases, tools, other), show each group as a collapsible section with its skill count, and offer a filter box.

#### Scenario: Grouping
- **WHEN** the profile has skills Kotlin, Spring Boot, PostgreSQL and Kafka
- **THEN** they appear under Languages, Frameworks, Databases and Messaging respectively

#### Scenario: Unknown skill
- **WHEN** a skill matches no keyword
- **THEN** it appears under Other

#### Scenario: Long list
- **WHEN** the profile has more than 20 skills
- **THEN** the groups start collapsed, each with its count

#### Scenario: Filter
- **WHEN** the user types "post" in the filter
- **THEN** only skills whose name contains it are shown, and groups with no match are hidden

#### Scenario: Editing in a group
- **WHEN** the user changes a skill's level, removes a skill or adds a new one inside the grouped view
- **THEN** the saved profile reflects the change and the saved order of skills is not altered by the grouping

#### Scenario: Parity with the CV export
- **WHEN** the shared fixture `skillGroups.cases.json` is run through the UI grouping and through the backend `SkillGroups`
- **THEN** both give the same group for every case
