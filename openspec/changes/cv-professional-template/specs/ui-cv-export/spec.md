## ADDED Requirements

### Requirement: Professional template and photo choice
The export page SHALL offer the template "Professional" besides the others. When it is chosen and the format is not Markdown, the page SHALL show a photo picker listing the user's JPEG files from the file cabinet (names only) with a "No photo" choice that is selected by default; the choice SHALL be sent as `photoFileId`. Without JPEG files the picker SHALL say so and point to the file cabinet. With another template or format the picker is hidden and nothing is sent. A `photo_invalid` answer SHALL show a message that names the problem without the file name.

#### Scenario: Picker
- **WHEN** the user chooses Professional and PDF
- **THEN** the picker lists the JPEG files, and the request carries the chosen id

#### Scenario: Hidden
- **WHEN** the user chooses Classic
- **THEN** no picker is shown and the request has no `photoFileId`
