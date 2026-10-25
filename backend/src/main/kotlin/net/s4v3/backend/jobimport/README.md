# jobimport

Purpose: import a job from a URL the user pastes (work in progress: only the fetching building blocks exist so far).

## Internal (`internal/`)
- `UrlRules`: accepts only https, port 443, no userinfo, a DNS name (no IP literal); builds the canonical URL (lower-case host, no fragment, no `utm_*`/`gclid`/`fbclid`, no trailing slash); refuses linkedin.com and indeed.com and their subdomains.
- `AddressRules`: public-address check (loopback, link-local incl. metadata, private, CGNAT, unique-local, multicast, unspecified, IPv4-mapped and NAT64 forms).
- `SafeFetcher`: resolves, requires every address to be public, connects a TLS socket to the validated address with SNI and certificate check for the name, GET over HTTP/1.1, at most 3 revalidated redirects, 10 s, 1 MB, HTML only. Failures are `FetchError` codes; URLs and content are never logged.
- `PageText`: jsoup to plain text, capped at 30 000 characters; under 300 characters is unreadable.
