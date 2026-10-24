# Security review: CV photo feature

Scope: design decisions 4, 6, 8, 10 of cv-professional-template and the code listed in the brief. Review by reading code; no production change. Throwaway runtime tests were not run (memory figures below are estimates from the JDK decoder model).

**No high findings.** Two mediums, several lows.

## Medium

### M1. Per-IP limits trust the first X-Forwarded-For entry
`PublicCvPageController.kt:225-228` (`clientIp`). The first XFF value is used when it is a valid literal. If the edge appends to a client-supplied XFF instead of replacing it, an attacker sends a fresh fake address per request and the `pageByIp` and `notFoundByIp` limiters never trigger. Only `pageByHandle` remains, and it is per handle, so many handles (or invented junk handles that skip it) give unlimited 404 and page traffic. The photo route shares the same limiters and each request does DB reads (`repo.findByHandle`, `profiles.get`, `cabinet.image`) before the cache is consulted. Fake keys also fill `WindowLimiter` (20k keys), after which every real new IP is refused (`WindowLimiter.kt` "full map takes no new key"): a limiter-exhaustion DoS for legitimate visitors.
Fix: take the last entry appended by the trusted proxy (or enable Spring `forward-headers-strategy` with a trusted-proxy list), and confirm the edge overwrites XFF. Document that assumption in design.md.

### M2. Decode memory and CPU budget is large for the caps allowed
`PhotoPipeline.kt:20-22, 59-68, 75-86`. 40 MP is allowed. A 40 MP JPEG can be tiny (solid colour, a few hundred KB), so the 6 MB cap does not bound pixels. Estimated heap per decode: decoded raster about 120 MB (3BYTE_BGR) plus `toRgb` copy 160 MB (`:88-99`), so 280 MB or more, times 2 permits (`RenderGuard.MAX_PARALLEL = 2`) while PDF rendering shares the same permits. Progressive JPEGs also use native coefficient memory outside the heap. The CMYK path (`readRasterAsRgb`) allocates raster 160 MB plus output 160 MB and runs a 40M-iteration `setRGB` loop (seconds of CPU). `OutOfMemoryError` is caught and mapped to `photo_invalid`, but an OOM can equally land in another thread (request, Hikari, GC pressure), so catching it is not containment. The permits (2) are held for the whole decode, so one authenticated user can starve exports and public renders: repeated exports with a big photo, and for the public side a page with a big photo re-decodes on each cache expiry (10 min) and on every request while permits are busy (`DefaultPublicCvService.kt:171-172`, `processPhoto` returns non-final, not cached). Starved requests wait `renderWait` each.
Fix: lower MAX_PIXELS to about 16 MP (a 4000x4000 photo is plenty for 320 px output); use `ImageReadParam.setSourceSubsampling` computed from the header size so the decode is already reduced (also removes the `toRgb` copy by asking for an INT_RGB destination type); do not fall back to `readRaster` unless the header says 4 components; run JVM with an explicit heap and test with a worst-case 12000x3333 file; cache a negative/busy result briefly per file hash.

## Low

### L1. Fallback decode runs a second full decode before the band check
`PhotoPipeline.kt:63-67`. Any non-warning exception from `read(0)` (for example a broken ICC profile on a normal RGB JPEG) triggers `readRaster` which decodes everything again, and only then `numBands != 4` rejects. Doubles the CPU and memory on a malformed file. Fix: check the component count from the metadata or the exception type first.

### L2. Transient 404 on the photo route when render permits are busy
`DefaultPublicCvService.kt:169-172, 154`. With no permit the page renders without a photo and `renderPhoto` returns null, so a valid published photo answers the uniform 404 (and a browser or crawler may cache it as missing; denyHeaders is no-store so only the browser retry matters). Not an information leak, a correctness issue; also timing differs (a photo-bearing handle can take up to `renderWait` or a decode, a plain 404 is fast), which tells a prober that a handle exists and has a photo. Handle existence is already public via the page, but "has a photo" vs "paused" is distinguishable by timing: a paused/locked/unpublished page returns before any file read, while a published one does DB plus file I/O. Fix: accept (document) or add a constant-time floor; low value.

### L3. Cabinet lookups happen on every public request before the cache
`DefaultPublicCvService.kt:163-168`. `photoFile` does a DB query and a file open (3-byte read) per request so a deleted or replaced file takes effect at once (this is a correct choice for staleness). Cost is bounded by the limiters (see M1). No stale photo found: the key carries id, size and sha256, and the file is read by the owner's sub. A replaced file with identical sha256 is the same bytes, so no poisoning. One gap: a photo file deleted while the key still hashes (race of at most one request) is harmless.

### L4. EXIF orientation is dropped, not applied
`PhotoPipeline.kt:68, 135-153`. Output has no metadata (writer is given null metadata: only a default JFIF header; EXIF, GPS, ICC, comments, thumbnails are all gone, so there is no metadata leak). Side effect: portrait phone photos appear rotated. Functional, not security. Fix only if wanted: read orientation before discarding.

### L5. Embedded ICC profile handled by the JDK colour engine
`PhotoPipeline.kt:60`. `reader.read` applies embedded ICC via JDK LittleCMS. Past JDK LCMS bugs exist; a malformed profile mostly throws (caught). Keep the JDK patched; optionally decode with the ICC ignored via the raster path for 3-band images.

### L6. Content-Disposition and filename
`CvExportController.kt:94` puts `cv.fileName` in the header. `slug()` output is `[a-z0-9-]` plus a fixed suffix, so no header injection. Public photo uses a constant `photo.jpg`. No finding, noted as verified.

### L7. Logging
`DefaultExportService.kt:73` logs only the exception class; photo ids and bytes are not logged; `PublicCvPageController.record` omits the ETag for photos. Verified, no finding. Minor: the first 8 hex chars of the page ETag are logged for pages; the page ETag is a hash that includes the base64 photo, so it is a hash of public content only. Acceptable.

## Verified, no issue
- IDOR: `cabinet.image(sub, id)` queries `owner_sub = ? and id = ?` (`JdbcFileRepository.find`), so foreign, missing, oversize, non-JPEG all return null. Export (`DefaultExportService.kt:91`) and settings save (`DefaultPublicCvService.kt:77`) answer the same `photo_invalid`; no branch differs in code, and the DB lookup is the same single query for foreign and missing ids, so no timing oracle beyond noise. Decode failures use the same code. `InvalidPhotoException` message is fixed.
- Public photo route: unpublished, paused, locked, no name, no photo, wrong template, unknown handle all return null from `renderServed` and give the one `notFound()` (same status, headers, body). Junk handles use the same notFound limiter. HEAD handled the same. The ETag exists only on a served photo and equals SHA-256 of the public bytes, so it is not an oracle for anything not already public. `/photo` is not shadowed by the catch-all (specific mapping wins); extra segments give the uniform 404.
- Cache: key includes sub, profile version, settings hash, template, language, photo id:size:sha256; invalidated on save, remove, takedown, unlock. Bounded 500 entries, 10 min TTL.
- PDF renderer: the URI resolver (`PdfRenderer.kt:30`) passes only `data:image/jpeg;base64,` and returns null for everything else (file:, http:, relative, `@font-face`, CSS `url()`); the HTTP factory refuses a second time. User text cannot create img, `url()`, `@import` or `@font-face`: all text goes through `esc` and the only dynamic CSS is the footer.
- Footer CSS escaping (`Templates.kt:97-101`): only ASCII letters, digits and space pass; every other code point is emitted as `\hex ` (trailing space terminates the escape), so a quote, backslash, newline, `}` or `</style>` cannot break out. Lone surrogates become a replacement escape. Correct.
- Public page CSP (`img-src data:`, no scripts, sandbox, frame-ancestors none) matches the data-URI photo; photo route CSP is `default-src 'none'; sandbox`; nosniff everywhere. HTML export meta CSP only adds `img-src data:` when a photo is present.
- Security config: only GET and HEAD on `/api/public/cv/**` are permitAll; export and settings need a token.
- UI: `JpegPhotoPicker` renders file names as React text (escaped); the export preview iframe is `sandbox=""` with srcDoc; no `dangerouslySetInnerHTML`.

## Lead verification

M1: verified on production that the edge overwrites a forged X-Forwarded-For, so the first entry is the real client address. No code change. M2, L1, L4 fixed (16 MP cap, subsampled decode, single decode, EXIF orientation). L2 already held (a busy result is not cached); documented in docs/cv-public.md.
