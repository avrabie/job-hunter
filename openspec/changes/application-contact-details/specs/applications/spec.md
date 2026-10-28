# Spec Delta

## ADDED Requirements

### Requirement: Company address and contact on an application
An application SHALL hold six optional details: company street, postal code, city, contact person, contact phone and contact email. They are `null` when unknown, are part of the application's detail, and are not part of the list. Applications created before this change and applications tracked from a posting start with all six `null`.

#### Scenario: New detail fields
- **WHEN** a user reads an application created before this change
- **THEN** all six details are `null` and nothing else about the application changed

#### Scenario: Not in the list
- **WHEN** a user lists their applications
- **THEN** the six details are not part of the list entries

### Requirement: Editing the details
A patch SHALL be able to set any of the six details, and a blank value SHALL clear it to `null`. An omitted field is left as it is. The limits are 200 characters for street, city and person, 40 for the phone, 254 for the email, 10 for the postal code, and the email SHALL look like an email address. A broken rule answers 400 with a fixed code and changes nothing.

#### Scenario: Set and clear
- **WHEN** the user patches contact person "Anna Muster" and later patches it with a blank string
- **THEN** the first read shows "Anna Muster" and the second shows `null`

#### Scenario: Invalid email
- **WHEN** a patch sets the contact email to "anna"
- **THEN** the response is 400 and the stored email is unchanged

#### Scenario: Another user's application
- **WHEN** user B patches the details of user A's application
- **THEN** the response is 404, the same as an unknown id, and A's details are unchanged

### Requirement: RAV proof of an applied application
`GET /api/applications/{id}/rav` SHALL answer, for the caller's application in stage `APPLIED`, the position (the title), the posting link, and the stored company address, postal code, contact person, phone and email, each `null` when missing. The company address SHALL be built from the parts that are present as "street, postal code city", so a missing street gives "postal code city" and city alone gives the city; with no part present it is `null`.

#### Scenario: All details stored
- **WHEN** the application is applied and has street "Bahnhofstrasse 1", postal code "8001", city "Zürich", a contact person, phone and email
- **THEN** the answer has company address "Bahnhofstrasse 1, 8001 Zürich", postal code "8001" and the contact fields

#### Scenario: Nothing stored
- **WHEN** an applied application has none of the six details
- **THEN** the company address, postal code and contact fields are `null` and the position and posting link are filled

#### Scenario: Not applied yet
- **WHEN** the application is in stage `INTERESTED`, `APPLYING` or `REJECTED`
- **THEN** the response is 409 `not_applied`

#### Scenario: Unknown or another user's application, or no token
- **WHEN** the id is unknown or belongs to another user
- **THEN** the response is 404; without a token it is 401
