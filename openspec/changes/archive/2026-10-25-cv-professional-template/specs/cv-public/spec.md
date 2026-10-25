## ADDED Requirements

### Requirement: Public photo
The public CV settings SHALL have an optional `photoFileId`, default null (off), like every other public field. When it is set, the template is `PROFESSIONAL` and the file is still the owner's JPEG, the public page SHALL show the processed round photo (same processing as the export) embedded in the page as a data URI, and `GET /api/public/cv/{handle}/photo` SHALL answer the same processed image as `image/jpeg` for sharing and caching. When the setting is null, the file is gone, no longer a JPEG, or the page is not served, the photo route SHALL answer the same uniform 404 as any unknown handle, with the same headers (`no-store` is replaced by the page's cache rules, never a longer cache than the page), the same per-IP limits and no body that tells the cases apart. Saving settings with a `photoFileId` that is not the caller's JPEG SHALL fail with 400 `photo_invalid` and save nothing. Deleting the file SHALL make the photo disappear from the page on the next request without an error. The photo SHALL be part of the render cache key (file id and its stored size and timestamp), so replacing the file shows the new photo. The audit trail SHALL record only that the photo flag is on, never the file id or name. Pausing or removing the page SHALL stop the photo route too. An administrator takedown SHALL stop it too.

#### Scenario: Off by default
- **WHEN** a page is published without choosing a photo
- **THEN** the page has no image and the photo route answers 404

#### Scenario: Photo shown
- **WHEN** the owner picks one of their JPEG files with the Professional template and publishes
- **THEN** the public page contains the photo and `/api/public/cv/{handle}/photo` answers 200 `image/jpeg`

#### Scenario: Not the owner's file
- **WHEN** the settings name another user's file
- **THEN** saving answers 400 `photo_invalid`

#### Scenario: File deleted
- **WHEN** the owner deletes the photo file
- **THEN** the page is served without a photo and the photo route answers 404

#### Scenario: Paused
- **WHEN** the page is paused
- **THEN** the photo route answers 404

### Requirement: Professional template on the public page
The template setting of the public page SHALL accept `PROFESSIONAL`.

#### Scenario: Selectable
- **WHEN** the owner saves the template `PROFESSIONAL`
- **THEN** the public page is rendered in that template
