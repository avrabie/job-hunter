# Spec Delta

## Purpose

Defines email-and-password accounts: open registration, form login, password reset and change, and the mail sender that delivers reset links.

## ADDED Requirements

### Requirement: Open registration
The server SHALL let anyone register with an email address, a password and optional first and last names through `POST /auth/api/register`, and SHALL create the account enabled with the role `USER`.

#### Scenario: Successful registration
- **WHEN** a client posts a valid email, a password of 8 to 72 characters and a name
- **THEN** the server responds 201 with the email, and the new user can sign in

#### Scenario: Email normalised
- **WHEN** a client registers `Anna@Example.COM`
- **THEN** the stored email is `anna@example.com`, and registering `anna@example.com` afterwards is rejected as a duplicate

#### Scenario: Duplicate email
- **WHEN** a client registers an email that already has an account
- **THEN** the server responds 409 and creates nothing

#### Scenario: Invalid input
- **WHEN** the email is malformed or the password is shorter than 8 or longer than 72 characters
- **THEN** the server responds 400 with the offending fields

#### Scenario: Concurrent duplicate
- **WHEN** two registrations with the same email arrive at the same time
- **THEN** exactly one account exists and the other request receives 409

### Requirement: Password storage
The server SHALL store passwords only as salted, adaptive hashes and SHALL NOT return or log a password or hash.

#### Scenario: Stored value
- **WHEN** an account is registered with a password
- **THEN** the stored password column holds a hash that differs from the password

### Requirement: Form login
The server SHALL sign a user in on its login page with email and password and SHALL treat a wrong password and an unknown email identically.

#### Scenario: Correct credentials
- **WHEN** a user submits their email and password on the login page during an authorization request
- **THEN** the authorization request resumes and the user is redirected back to the client with a code

#### Scenario: Wrong password or unknown email
- **WHEN** a user submits a wrong password, or an email without an account
- **THEN** the login page shows the same generic error in both cases

#### Scenario: Google-only account
- **WHEN** someone submits an email that belongs to an account created through Google, with any password
- **THEN** the login fails with the same generic error

### Requirement: Password reset request
`POST /auth/api/password-reset/request` SHALL answer 200 whether or not the email has an account, and SHALL send a one-time reset link only for accounts that have a local password.

#### Scenario: Known email
- **WHEN** a reset is requested for a registered email
- **THEN** the response is 200 and one reset message is handed to the mail sender

#### Scenario: Unknown email
- **WHEN** a reset is requested for an email without an account
- **THEN** the response is 200 with the same body and no message is sent

#### Scenario: Google-only account
- **WHEN** a reset is requested for an account created through Google
- **THEN** the response is 200 and no message is sent

### Requirement: Reset token
A reset token SHALL be random with at least 256 bits, SHALL be stored only as a hash, SHALL expire after 30 minutes, and SHALL be usable once.

#### Scenario: Hash only
- **WHEN** a reset is requested
- **THEN** the database holds the token's hash and never the token itself

#### Scenario: Expired token
- **WHEN** a token is used 31 minutes after it was issued
- **THEN** the confirmation is rejected with a generic invalid-or-expired error

#### Scenario: Reuse
- **WHEN** a token that was already used successfully is submitted again
- **THEN** the confirmation is rejected

### Requirement: Password reset confirmation
`POST /auth/api/password-reset/confirm` SHALL set the new password, validated like registration, when given a valid token, and SHALL then allow sign-in only with the new password.

#### Scenario: Successful reset
- **WHEN** a valid token and a valid new password are submitted
- **THEN** the response is 204, the old password stops working and the new one works

#### Scenario: Weak new password
- **WHEN** the new password is shorter than 8 characters
- **THEN** the response is 400 and the token stays usable

#### Scenario: Unknown token
- **WHEN** a token that was never issued is submitted
- **THEN** the response is the same generic error as for an expired token

### Requirement: Password change
`PUT /auth/api/me/password` SHALL change the signed-in user's password when the current password is correct and the new one differs and is valid, and SHALL act only on the user identified by the access token's `sub`.

#### Scenario: Successful change
- **WHEN** a user supplies the correct current password and a different valid new password
- **THEN** the response is 204 and only the new password works

#### Scenario: Wrong current password
- **WHEN** the current password is wrong
- **THEN** the response is 400 or 403 with an error and the password is unchanged

#### Scenario: Same password
- **WHEN** the new password equals the current one
- **THEN** the request is rejected and nothing changes

#### Scenario: Google-only account
- **WHEN** a user whose account was created through Google calls the endpoint
- **THEN** the request is rejected as having no password to change

#### Scenario: User A cannot change user B
- **WHEN** user A calls the endpoint with A's token and B's email or id in the request
- **THEN** only A's password can be affected, and B's password is unchanged

### Requirement: Mail sender interface
The server SHALL send emails (password reset link, password changed notice) through one `MailSender` interface, and until SMTP is configured SHALL use an implementation that writes the message to the application log.

#### Scenario: Logging sender
- **WHEN** no SMTP settings are configured and a reset is requested
- **THEN** a message containing the reset link is written to the log by the logging sender, and nothing leaves the server

#### Scenario: Link target
- **WHEN** a reset message is composed
- **THEN** the link points to the configured UI base URL with the token as a query parameter

#### Scenario: Notice after change
- **WHEN** a password is changed or reset
- **THEN** a "password changed" message is handed to the mail sender after the change has committed

### Requirement: Mail only after commit
Messages SHALL be handed to the mail sender only after the database transaction that caused them has committed.

#### Scenario: Rolled back
- **WHEN** a password reset request fails and its transaction rolls back
- **THEN** no message is handed to the mail sender
