package net.s4v3.backend.jobimport.internal

/** Fixed error codes of the URL rules and the fetcher. The code is the only thing that is ever reported or logged. */
internal enum class FetchError(val code: String) {
    INVALID_URL("invalid_url"),
    UNSUPPORTED_SITE("unsupported_site"),
    BLOCKED_ADDRESS("blocked_address"),
    UNREACHABLE("unreachable"),
    NOT_HTML("not_html"),
    TOO_LARGE("too_large"),
}
