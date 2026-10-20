# auth-google-login Specification

## Purpose
Defines sign-in with Google, including automatic account creation and the rules that stop a Google login from taking over, or being taken over through, an existing account.

## Requirements

### Requirement: Google login offered when configured
The login page SHALL offer "Sign in with Google" only when a Google client id and secret are configured, and the server SHALL start and work normally without them.

#### Scenario: Configured
- **WHEN** Google credentials are set
- **THEN** the login page shows the Google option

#### Scenario: Not configured
- **WHEN** Google credentials are absent
- **THEN** the login page shows only the email and password form and the server starts

### Requirement: Automatic account creation
When a Google sign-in succeeds for an email with no account, the server SHALL create an enabled account with the role `USER`, taking email and names from Google, and SHALL continue the authorization request as that user.

#### Scenario: First Google sign-in
- **WHEN** a user with a verified Google email and no account signs in with Google
- **THEN** one account exists for that email, with provider Google and the Google subject stored, and the SPA receives tokens for it

#### Scenario: Returning Google user
- **WHEN** the same Google user signs in again
- **THEN** the same account and the same `sub` are used and no second account is created

#### Scenario: Names refreshed
- **WHEN** the Google profile names changed since the last sign-in
- **THEN** the stored names are updated

#### Scenario: Concurrent first sign-ins
- **WHEN** two first sign-ins for the same Google user arrive at the same time
- **THEN** exactly one account exists afterwards

### Requirement: Verified email required
The server SHALL reject a Google sign-in whose email is not verified by Google.

#### Scenario: Unverified email
- **WHEN** Google reports `email_verified` as false
- **THEN** no account is created or linked and the user sees a sign-in error

### Requirement: No password for Google accounts
Accounts created through Google SHALL have no usable password until the user sets one through password reset, and SHALL NOT be reachable through the email and password form.

#### Scenario: Form login attempt
- **WHEN** someone submits the email of a Google-created account and any password on the login form
- **THEN** the login fails

### Requirement: Safe linking to an existing account
If a Google sign-in's verified email matches an existing local account, the server SHALL link the Google subject to that account and keep its `sub`, and SHALL discard that account's local password so that whoever registered it with an email they might not own cannot keep access.

#### Scenario: Link
- **WHEN** a user who registered with email and password signs in with Google using the same verified email
- **THEN** both methods resolve to the same account and the same `sub`

#### Scenario: Squatter loses access
- **WHEN** someone registered `victim@example.com` with a password of their own and the real owner then signs in with Google
- **THEN** the earlier password no longer works, and the owner can set a new one through password reset

#### Scenario: Linked account keeps working
- **WHEN** the linked user signs in with Google again
- **THEN** sign-in succeeds with the same `sub`

### Requirement: No Google data beyond need
The server SHALL store only the Google subject, email, given name and family name, and SHALL NOT store or log Google access or ID tokens.

#### Scenario: Stored fields
- **WHEN** a Google account is created
- **THEN** the user row holds those four values and no Google token
