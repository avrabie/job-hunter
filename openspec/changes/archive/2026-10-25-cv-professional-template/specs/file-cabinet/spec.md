## ADDED Requirements

### Requirement: Read an own JPEG for another module
The files module SHALL offer other modules `FileCabinet.image(sub, id)`, which returns the user's own file `id` (details and path of its bytes) only when the file belongs to `sub`, and its first bytes are those of a JPEG (`FF D8 FF`). For a file that is missing, belongs to another user, is not a JPEG or is larger than 6 MB it SHALL return null, without saying which. The caller reads the bytes and never writes them. The method SHALL NOT log file names.

#### Scenario: Own JPEG
- **WHEN** a user uploaded a JPEG named `photo.jpg` and another module asks for it by id
- **THEN** it receives the details and a readable path

#### Scenario: Other users and other types
- **WHEN** the id belongs to another user, or the file is a PDF or a PNG named `x.jpg`
- **THEN** the result is null
