## ADDED Requirements

### Requirement: Professional template
The export SHALL offer a fourth template `PROFESSIONAL` for HTML and PDF. It SHALL print the full name in navy capitals, the headline in orange, the contact lines under them, an optional round photo at the top right, section headings in navy capitals with letter spacing and a rule, the summary justified, experience entries with the title on the left and the period on the right and the company and place on the line below (the company in orange, the place right-aligned in italics), square bullets, and in the PDF a footer on every page with the name and headline. Every section and option of the other templates SHALL work with it (section choice and order, contact visibility, language, open source, analysis). The Markdown format SHALL ignore the template, as before.

#### Scenario: Professional PDF
- **WHEN** a profile with experience and skills is exported as PDF with the template `PROFESSIONAL`
- **THEN** the bytes start with `%PDF-`, the text contains the name, every experience title and the footer, and the colours of the template appear in the stylesheet

#### Scenario: Other templates unchanged
- **WHEN** the same profile is exported with `CLASSIC`, `MODERN` or `COMPACT`
- **THEN** the output is byte-identical to the output before this change

### Requirement: Grouped skills
In the Professional template the skills section SHALL be a two-column table: the group name on the left and the skills of that group on the right, in the order the user typed them. Groups SHALL come from a fixed built-in keyword list that is matched case-insensitively on whole words and symbols; a skill that matches no group SHALL go under the group "Other" (German "Weitere"), which is printed last. A skill appears in exactly one group. Empty groups are not printed. The skill level label SHALL still follow the skill as in the flat list. Group names SHALL follow the CV language.

#### Scenario: Grouping
- **WHEN** the skills are `Java`, `Kafka`, `PostgreSQL`, `Basket weaving`
- **THEN** Java is under Programming languages, Kafka under Messaging, PostgreSQL under Databases and Basket weaving under Other

#### Scenario: Flat in other templates
- **WHEN** the same skills are exported with `CLASSIC`
- **THEN** they print as the flat comma list as before

### Requirement: Photo
The export options SHALL accept an optional `photoFileId`, the id of one of the caller's own files. When given and the template is `PROFESSIONAL` and the format is HTML or PDF, the CV SHALL show that image at the top right as a round photo. The file SHALL be read only if it belongs to the caller and is a JPEG by its content (not by its name or declared type); any other case (not found, not the caller's, not a JPEG, too large, damaged) SHALL be refused with a 400 error `photo_invalid` and no export. The server SHALL re-encode the image: centre-crop to a square, scale to 320 by 320 pixels, apply a round mask on a white background and encode it as a new JPEG, so no metadata of the original (EXIF, GPS, thumbnails, colour profiles) reaches the output. Images larger than 6 MB, wider or taller than 12 000 pixels, or with more than 16 million pixels SHALL be refused before decoding. With another template or format the option SHALL be ignored. In HTML the photo SHALL be embedded as a `data:image/jpeg;base64` URI and the content security policy of the document SHALL allow `img-src data:` only when a photo is present.

#### Scenario: Photo in the PDF
- **WHEN** a JPEG file of the caller is given with the Professional template as PDF
- **THEN** the PDF contains one image

#### Scenario: Not a JPEG
- **WHEN** the file is a PNG renamed to `.jpg`
- **THEN** the answer is 400 `photo_invalid`

#### Scenario: Someone else's file
- **WHEN** the id belongs to another user
- **THEN** the answer is 400 `photo_invalid`, the same as for an unknown id

#### Scenario: Metadata is gone
- **WHEN** a JPEG with EXIF GPS data is used
- **THEN** the embedded photo contains no EXIF segment

#### Scenario: Ignored elsewhere
- **WHEN** the template is `CLASSIC` or the format is Markdown
- **THEN** no image is read and nothing changes
