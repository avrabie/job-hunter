# api-docs Specification

## Purpose
Describes the backend's REST API in one generated, always-current OpenAPI document and a browsable page, and keeps them complete as endpoints are added.

## Requirements

### Requirement: OpenAPI document
The backend SHALL serve an OpenAPI 3 document describing its REST API at `GET /api/openapi.json`, generated from the code. The document SHALL list every endpoint under `/api` that the application serves (except the documentation itself) with its method, path, a one-line summary, request body schema, response schemas for success and for each error status it can return, and whether it needs a bearer token and which role.

#### Scenario: Document is served
- **WHEN** `GET /api/openapi.json` is requested
- **THEN** the answer is 200 with a JSON document whose `openapi` field starts with `3.`

#### Scenario: Profile endpoints described
- **WHEN** the document is read
- **THEN** it contains `GET` and `PUT` for `/api/profile`, the profile request and response schemas, and the error statuses 400, 401, 409 and 413 for `PUT`

#### Scenario: Admin endpoints described
- **WHEN** the document is read
- **THEN** it contains `GET` and `POST` for `/api/admin/crawl-schedule` marked as requiring a bearer token and the `ADMIN` role, with 400, 401 and 403 described

#### Scenario: Bearer authentication declared
- **WHEN** the document is read
- **THEN** it declares an HTTP bearer security scheme and the secured operations reference it

### Requirement: Browsable documentation page
The backend SHALL serve a browsable page for the document at `GET /api/docs`, with an Authorize control that accepts a bearer token and sends it with the calls made from the page. The page and its static files SHALL be served under `/api`.

#### Scenario: Page loads
- **WHEN** `GET /api/docs` is requested
- **THEN** the answer is an HTML page that loads the document from `/api/openapi.json`, and every static file it needs is served from a path under `/api`

#### Scenario: Calls from the page carry the token
- **WHEN** a user pastes an access token into Authorize and runs `GET /api/profile` from the page
- **THEN** the request carries that bearer token

### Requirement: Documentation is readable without a token
The document and the page SHALL be readable without an access token. No other endpoint SHALL become readable without one because of this.

#### Scenario: Anonymous read
- **WHEN** `GET /api/openapi.json` and `GET /api/docs` are requested without a token
- **THEN** both answer 200

#### Scenario: Data endpoints stay protected
- **WHEN** `GET /api/profile` and `GET /api/admin/crawl-schedule` are requested without a token
- **THEN** both answer 401

#### Scenario: Only reads are open
- **WHEN** a `POST` to `/api/openapi.json` or `/api/docs` is sent without a token
- **THEN** the answer is 401 or 405 and nothing changes

### Requirement: Nothing sensitive in the documentation
The document and page SHALL NOT contain real personal data, credentials, tokens, cluster addresses or the content of any stored profile or posting. Examples SHALL be synthetic.

#### Scenario: Examples are synthetic
- **WHEN** the document is read
- **THEN** every example value is synthetic and no stored data of any user appears in it

### Requirement: The documentation cannot fall behind the code
The build SHALL fail when a controller endpoint under `/api` that the application serves is missing from the generated document or has no summary.

#### Scenario: Every endpoint is documented
- **WHEN** the application's request mappings under `/api` (other than the documentation paths) are compared with the generated document
- **THEN** each mapping appears in the document with a non-empty summary

#### Scenario: A new undocumented endpoint
- **WHEN** a controller endpoint is added without a summary
- **THEN** the guard test fails and names the endpoint

### Requirement: Documentation can be switched off
A setting SHALL turn the document and the page off. When off, neither path exists: an authenticated request to them answers 404, an anonymous one answers 401 like any other unknown protected path, and the rest of the API SHALL be unchanged.

#### Scenario: Switched off
- **WHEN** the documentation setting is off
- **THEN** an authenticated `GET /api/openapi.json` or `GET /api/docs` answers 404, an anonymous one answers 401, and `GET /api/profile` still answers 401 without a token
