# Spec Delta

## Purpose
Defines the file cabinet on the profile page: listing, uploading with progress, downloading and deleting files, and choosing the master CV.

## ADDED Requirements

### Requirement: File list
The profile page SHALL show a "Your files" section that loads `GET /api/files` through the API client and lists each file with its name, kind (CV or Other), size in a readable unit, upload date and, for the master CV, a "Master CV" label. The list SHALL have loading, empty and error-with-retry states.

#### Scenario: Empty cabinet
- **WHEN** a user without files opens the page
- **THEN** the section says there are no files and offers the upload

#### Scenario: Files listed
- **WHEN** the user has a master CV and an `OTHER` file
- **THEN** both are listed, the CV carries the "Master CV" label and sizes are shown as KB, MB or GB

### Requirement: Upload with progress
The section SHALL let the user choose a file and its kind ("CV" or "Other file"; CV accepts `.pdf`, `.md` and `.txt`), upload it with a visible progress indicator and a Cancel button, and show the new file in the list. Server rejections (400, 413, 507) SHALL be shown as short fixed messages. The page SHALL NOT read the file into memory.

#### Scenario: Upload an Other file
- **WHEN** the user picks a file with kind "Other file"
- **THEN** a progress indicator shows while it uploads and the file appears in the list afterwards

#### Scenario: Cancel
- **WHEN** the user cancels during the upload
- **THEN** the request is aborted and the list is unchanged

#### Scenario: Rejected
- **WHEN** the server answers 413 or 507
- **THEN** a fixed message explains it and the list is unchanged

### Requirement: Download and delete
Each file SHALL have a Download button that asks for a download ticket and starts the browser download from the returned address without reading the file into page memory, and a Delete button that asks for confirmation. After a deletion the list SHALL be refreshed.

#### Scenario: Download
- **WHEN** the user presses Download
- **THEN** a ticket is requested with the user's token and the browser is pointed at the returned address

#### Scenario: Delete
- **WHEN** the user confirms the deletion
- **THEN** the file disappears from the list

### Requirement: Master CV choice
Each CV file that is not the master SHALL have a "Use as master CV" button, and the master SHALL have a "Remove master" button. The choice SHALL be shown immediately after the server confirms it.

#### Scenario: Choose
- **WHEN** the user presses "Use as master CV" on a CV file
- **THEN** that file carries the "Master CV" label and the previous master does not

#### Scenario: Remove
- **WHEN** the user presses "Remove master"
- **THEN** no file carries the label and the fill button is disabled
