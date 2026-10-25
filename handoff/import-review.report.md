# Security and privacy review: job import (A) and tracking/letters (B)

Read-only review of main at 1459813. Nothing under `private/` opened. No code run; findings come from reading code (I did not execute fetcher or jsoup tests).
Paths are relative to `backend/src/main/kotlin/net/s4v3/backend/` unless stated.

## Summary
The SSRF and fetch layer is solid. The weak area is the shared-posting trust model: any user can write arbitrary content into a posting that every user sees, matches against and feeds to the letter LLM. Grounding and the "URL cannot be edited" rule do not prevent this. Per-user IDOR is clean in both scopes.

## HIGH

### H1. Any user can plant arbitrary content under any URL (pasted text is never tied to the URL; edits are ungrounded)
- `jobimport/internal/DefaultJobImportService.kt:66-77` (pasted `text` path skips the fetch but still takes any syntactically valid https URL), `:107-110` (edits replace title, company, description with only length and `<[a-zA-Z/!]` checks), `:113-130`.
- Grounding (`:176-185`) runs only at preview against the text the same user supplied, and the user can edit the draft afterwards. So grounding gives zero protection against a malicious user.
- Scenario: attacker submits `{url:"https://realcompany.ch/jobs/123", text:"<300+ chars of anything>"}`, edits the description to a phishing or prompt-injection payload, and confirms. A `user-import`/primary posting is created with `url` = the real company link. The row is then:
  - visible to all signed-in users (`web/internal/PostingsController.kt`, `userAdded` flag exposed; the UI shows "Added by a user" only as a label);
  - evaluated against every profile (`postings/internal/JdbcPostings.kt:151` publishes `JobIngested`, `matching/internal/MatchListeners.kt:13`);
  - frozen into every victim's application when they track it (`DefaultApplicationService.kt:46-47`), and then sent to the letter model (see M2).
- First-wins squat: later imports of the same canonical URL hit `findImported` (`:115-120`) and silently reuse the attacker's posting, discarding the later user's correct text and edits. The real posting can never repair it (no one but the importer owns it, and `refresh` only lets the primary source change it, `JdbcPostings.kt:90`). The attacker can also pre-claim URLs that do not exist yet.
- Dedupe-key squat: user-import insert is primary when no posting has that `dedupe_key`. Choosing company/title/city freely lets an attacker claim the key, so later real crawler postings with the same key attach as non-primary (`JdbcPostings.kt:125-128`) and never replace the attacker's content.
- Fix (pick a combination):
  1. Pasted-text imports must not be keyed by a URL that was not fetched: either require the URL to be fetched and verified (store the fetched text hash and refuse edits of the description beyond whitespace, or re-run grounding at confirm on description-vs-original-page-text stored in the draft), or for pasted text use a private posting (not shared, `sourcePostingId` = hash of the text + sub, visible only to the owner).
  2. Re-run `Grounding` at confirm against the stored page text, and bound edits (e.g. edit distance).
  3. Do not let a user-import be `is_primary` over a crawler or later source: make crawler sources able to take over (primary swap) and allow the owner or an admin to delete.
  4. Show "unverified, added by a user" on matches, not only on the list.
  5. Add an admin takedown path (`posting_import` already records the importer).

## MEDIUM

### M1. Confirm race overwrites another user's posting through `ingest` refresh
- `DefaultJobImportService.kt:115-130`. `findImported` is a check-then-act. Two users confirming the same canonical URL concurrently both see `known == null`; the advisory lock in `JdbcPostings.ingest` serialises them, but the second takes the `refresh` branch with `existing.primary == true` (`JdbcPostings.kt:90-119`) and rewrites title, company, description, city of the first user's posting (also affecting everyone who tracked it, via `liveChanged`). This is exactly what the code comment on line 113 says must not happen.
- Fix: do the existence check inside the same transaction and lock (add an `ingestIfAbsent`/`insertOnly` mode to `PostingIngest`, or take the advisory lock `user-import:<sha>` around find-then-ingest).

### M2. Prompt-injection surface for victims: shared posting text goes into the letter prompt
- `llm/internal/LetterPrompts.kt:40-41,95-96,103-105`. Title and company only get whitespace-collapsed (`clean`), and the description only has exact `<job_description>` / `</job_description>` removed (`TAGS`). Variants `</job_description >`, `< /job_description>`, `</JOB_DESCRIPTION\n>` are not stripped. Import's own HTML check (`<[a-zA-Z/!]`, `DefaultJobImportService.kt:171`) does not match `< /`, and crawler data is not checked at all.
- Consequences: a poisoned description (H1) can try to make victims' letters include attacker links, claims, or text. `LetterCleaner` (`KoogLetterWriter.kt:70-81`) rejects HTML tags only; Markdown links (`[x](https://evil)`, `javascript:`) pass. The UI renders the letter (I did not review the UI renderer; if it turns Markdown into HTML, restrict link schemes).
- Mitigations in place: the delimiter + "untrusted data" instruction is good practice and there are no tools, so the blast radius is letter text only.
- Fix: strip all `<`, `>` variants or neutralise any `<...>` that looks like one of your tag names case-insensitively with optional whitespace (`<\s*/?\s*(job_description|candidate_facts|instructions|previous_letter|review_comments)\s*>`); apply the same strip to title/company; post-filter the letter for URLs and Markdown links unless present in candidate facts.

### M3. Page text is not delimiter-sanitised in the extractor prompt
- `llm/internal/JobPrompts.kt:23-28`. Unlike `LetterPrompts.description` and `KoogProjectAnalyst.REPOSITORY_TAG`, `user()` does not remove `</page_text>`. A fetched page or pasted text can close the tag and add instructions. Impact is bounded (JSON-only output, cleaner, grounding on description, user review), but title, company, location are never grounded, so injected values pass. Fix: strip `</?page_text>` (case-insensitive, whitespace-tolerant) from `pageText`; also ground company and title (each token should appear in the page).

### M4. No concurrency or global cap on fetch and parse work; limits count calls, not in-flight
- `jobimport/internal/ImportLimiter.kt`, `SafeFetcher.kt:213`, `PageText.kt:16`. A single user may fire 20 previews in parallel; each holds a Tomcat thread up to 10 s fetch and then up to 60 s LLM wait (`JobImportSettings.timeout`). Per request memory: up to 1 MB bytes, a 1-2 MB String, a jsoup DOM (typically 10-30x the HTML size), plus `PageText` copies. With a few users this is a cheap thread/heap exhaustion lever, and every preview is also a paid model call (Anthropic cost cap is 20/h/user, with unlimited signups unless registration is restricted).
- jsoup 1.23.2 limits tree depth by default (not verified empirically); worth a unit test with 1 MB of nested `<div>`.
- Fix: a semaphore (global, e.g. 4 fetch+parse at once, and 1 per user), reject with 429 when busy; parse with `Parser.htmlParser().setMaxDepth`/`setMaxErrors`; cap the pasted `text` length before trimming (JSON string up to Jackson's 20 MB default is read first; there is no request-size limit anywhere: `application.yaml:12` only sets multipart).

## LOW

### L1. `postingExisted` / `postingId` reveal what others imported
- `DefaultJobImportService.kt:129,138`. Probing a URL tells whether any user imported it (or whether a crawler posting with that company/title/city key exists), and `drafts.recordImport` (`:136`) attributes the first importer to a posting a user only matched, since `on conflict do nothing` keeps the first row, but for a crawler posting that no one imported the row is created for whoever confirms first, so `posting_import.user_sub` is not "who added the posting" for non-created outcomes. Fix: record the import only when the posting was newly created by this call, or add an `outcome` column; keep `posting_import` out of every API (verified: no reader other than `ImportDrafts`).

### L2. Dedupe attachment lets a user keep stale crawler postings alive
- `JdbcPostings.kt:126-127` `touch(canonical)` on a non-primary attach. Importing a URL whose company/title/city key matches an expired posting resets its `last_seen` and hides GONE status (`DefaultApplicationService.kt:101-105`) for other users who track it. Fix: do not touch on user-import attach.

### L3. Address filter gaps (defence in depth only; egress policy and DNS-resolution check mean no exploit found)
- `jobimport/internal/AddressRules.kt:18-39`. Not blocked: IPv4 documentation ranges (192.0.2/24, 198.51.100/24, 203.0.113/24), 192.88.99/24; IPv6 documentation `2001:db8::/32`, 6to4 `2002::/16` and Teredo `2001::/32` (embedded IPv4 not inspected), NAT64 local-use `64:ff9b:1::/48`, IPv4-compatible `::a.b.c.d` (`::/96` except `::` and `::1`), discard `100::/64`. Fix: add these denylist entries, and for 6to4/Teredo evaluate the embedded v4.
- `k8s/base/backend-egress.yaml:41-52` is IPv4 only (`0.0.0.0/0`), fine for an IPv4 cluster; if the cluster is dual-stack add an equivalent `::/0` block with `fc00::/7`, `fe80::/10` excepted, or deny IPv6. Also exclude `0.0.0.0/8`, `192.0.0.0/24`, `198.18.0.0/15`, `224.0.0.0/3` to match `AddressRules`. The policy is only as good as the pod/service CIDRs being inside the excepted RFC1918 ranges: confirm the cluster's pod CIDR and service CIDR are in `10/8` or `172.16/12` (the k3s defaults 10.42/10.43 are).

### L4. Only the first resolved address is tried
- `SafeFetcher.kt:78-88`. All records must be public (good), but a host with one dead A/AAAA first record fails with UNREACHABLE. Availability only; try the validated list in order.

### L5. `WindowCounter` with limit 0 throws
- `ImportLimiter.kt:27`: `window.first()` on an empty deque when `limit() == 0` -> `NoSuchElementException` -> 500. `UserHourlyLimiter.kt:15-18` is fine (returns false). Also both limiters are in-memory, per instance and reset on restart; per-user maps are never evicted. Acceptable while single-instance (documented).

### L6. LLM client failure text is logged
- `KoogJobExtractor.kt:57-60` and `KoogLetterWriter.kt:54-57` log `describeClientFailure(e.message)` (`llm/internal/KoogCvReader.kt:36-45`), i.e. the provider's error `message` field. Anthropic invalid-request messages are usually generic, but they can quote request fragments (token/length hints). Fix: log status and error type only, drop the explanation, or cap it at e.g. 80 chars.

### L7. Unbounded request bodies
- No `max-http-request-size`/body filter for JSON endpoints (`preview.text`, `letters/edited.text`, `notes`). Size checks happen after Jackson materialises the string (up to its 20 MB default string cap). Fix: a servlet filter or edge `client_max_body_size` (e.g. 256 KB for `/api/**` except uploads).

## INFO

- `applications/internal/LetterFileName.kt`: file name is ASCII `[a-z0-9-]`, <= 95 chars; no header injection or path traversal; `nosniff`, `text/markdown`, `no-store` set (`LetterController.kt:159-163`).
- Instruction and comment text only influences the caller's own letters (self-injection).
- `ImportDrafts.TTL` 1 h, purge hourly; `find` also filters `expires_at > now` so an unpurged draft cannot be confirmed late.
- `removeUserData` is not wired to any account-deletion flow yet (README acknowledges it); `posting_import.user_sub` and `job_import_draft.owner` are raw subs and need deleting on account deletion; `application`/`motivation_letter` rows likewise.

## Verified safe (what I tried to break)

URL and SSRF
- Decimal/hex/octal/short IPv4 literals, IPv6 in brackets, trailing dots, `localhost`, unicode/IDN hosts, `user@host`, backslashes, non-443 ports, `http`, scheme-relative URLs: `UrlRules.kt:19-43` rejects all (strict DNS-label regex, last label must contain a letter, `URI` rejects non-ASCII hosts and backslashes, `rawUserInfo` must be null, port must be -1 or 443). IDN is accepted only as punycode.
- Names that resolve to private addresses (`*.nip.io`, `internal.svc.cluster.local`, glibc-style `0x7f.0.0.0x1` handed to the resolver): every resolved address is checked with `AddressRules.isPublic` before connecting (`SafeFetcher.kt:83-84`) and ALL records must be public. IPv4-mapped IPv6 and NAT64 `64:ff9b::/96` are unwrapped and checked (`AddressRules.kt:31-32`); Java also normalises mapped addresses to `Inet4Address`.
- DNS rebinding: one resolution per hop and the socket connects to that validated `InetAddress` (`:88`), no second lookup. SNI and hostname verification are set from the validated host (`:89-93`, `endpointIdentificationAlgorithm = "HTTPS"`), default JVM trust store; no TrustManager/HostnameVerifier override anywhere in `main`.
- Redirects: max 3, each hop is re-validated with `UrlRules` and re-resolved; `http://` downgrade -> INVALID_URL; relative Location resolved against the validated base; malformed Location (`IllegalArgumentException`) is caught; no credentials or cookies are ever sent.
- Request smuggling/CRLF: the request is a fixed GET with only `host` (regex-validated) and `target`; raw CR/LF/space are illegal in `URI` and fail parsing, non-ASCII becomes `?` via US_ASCII, and `Connection: close`, no body. User-Agent comes from config.
- Response handling: status 2xx only, content type allow-list, `Content-Length` and chunk sizes checked against 1 MB before reading (negative and huge chunk sizes handled), 8 KB line cap, 100 header cap, total 10 s deadline re-applied before every read (slowloris bounded), `Accept-Encoding: identity`; a server ignoring it yields garbage text that fails the readable-text/LLM steps, no decompression bomb possible. Unknown/odd charset falls back to UTF-8 (`UnsupportedCharsetException` is an `IllegalArgumentException`).
- Error and log hygiene: `JobImportException` message is the fixed code; `SafeFetcher.Fail` has no stack trace or message; the controller returns fixed messages; the service logs only code, draft id and duration; the fetcher logs nothing. `posting_import` has no reader other than the repository (never returned by any API).
- Draft IDOR: `find`, `delete` are by id AND owner; foreign or random ids give the same `draft_not_found` as expired ones; `draftId` parsing failure is the same answer. Owner is always `currentUser.id()`, the confirm body carries no user field, and the URL cannot be edited after preview.
- Rate limits: keyed by token `sub`, not by IP, so `X-Forwarded-For` is irrelevant; counted before the fetch and before the model call, failures also count. Confirm limit is checked after edit validation, so invalid edits are free (harmless).
- ImportError messages and statuses do not echo input.

Scope B (applications and letters)
- IDOR: every repository statement in `ApplicationRepository.kt` and `LetterRepository.kt` filters by `owner_sub`; service methods use the token `sub` everywhere; `byPosting` returns only the caller's rows; letters `get/list/download/delete/generate(basedOnVersion)` resolve the caller's application or return null/404 (not 403), so no existence oracle between users. `track` of a posting the caller does not track copies the posting at that moment (frozen copy is by design).
- Per-user advisory locks serialise the 500 application cap and the 20-version cap; model calls run outside transactions; limiter is checked before the model call; the model output is size and HTML checked; `selection` indexes are bounds-checked (negative included); duplicates, counts and text sizes bounded.
- Logs in `DefaultApplicationService` and `DefaultLetterService` contain no ids, titles, notes or text.
- Download name and headers safe (see Info).
- PATCH notes capped at 5 000 chars and appliedOn parsed strictly; stage is an enum lookup; error bodies are fixed codes.
