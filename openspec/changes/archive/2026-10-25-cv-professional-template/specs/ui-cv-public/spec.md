## ADDED Requirements

### Requirement: Public photo choice
The Public CV page SHALL offer the template "Professional" and, when it is chosen, a photo picker of the user's JPEG files with "No photo" selected by default. Next to the picker the page SHALL state that the photo becomes visible to everyone with the link. The choice is part of the saved settings and the preview. A `photo_invalid` answer SHALL show a message without the file name.

#### Scenario: Warning shown
- **WHEN** the owner picks a photo
- **THEN** the page says the photo is public once published

#### Scenario: Saved with the settings
- **WHEN** the owner saves with a photo
- **THEN** the request carries `photoFileId` and the preview shows the photo
