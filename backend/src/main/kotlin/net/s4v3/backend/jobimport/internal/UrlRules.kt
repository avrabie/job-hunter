package net.s4v3.backend.jobimport.internal

import java.net.URI

/** A URL that passed [UrlRules]: [host] is lower case, [target] is the raw path and query to request. */
internal data class CheckedUrl(val host: String, val target: String, val canonical: String)

internal sealed interface UrlCheck {
    data class Ok(val url: CheckedUrl) : UrlCheck
    data class Rejected(val error: FetchError) : UrlCheck
}

/** Which URLs may be fetched at all, and the canonical form used to recognise the same job again. */
internal object UrlRules {
    private const val MAX_LENGTH = 2048
    private val denied = listOf("linkedin.com", "indeed.com")
    private val hostPattern = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /**
     * What a person typed or pasted, made checkable: a missing scheme becomes `https://` and `http://` is upgraded to
     * `https://` (we never fetch over plain http). Redirect targets do not go through this, so a downgrade stays rejected.
     */
    fun normalizeInput(raw: String): String {
        val text = raw.trim()
        return when {
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring("http://".length)
            "://" in text -> text
            else -> "https://$text"
        }
    }

    fun check(raw: String): UrlCheck {
        if (raw.length > MAX_LENGTH) return UrlCheck.Rejected(FetchError.INVALID_URL)
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return UrlCheck.Rejected(FetchError.INVALID_URL)
        }
        val host = uri.host?.lowercase()
        val valid = uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            host != null && isDnsName(host)
        if (!valid || host == null) return UrlCheck.Rejected(FetchError.INVALID_URL)
        if (denied.any { host == it || host.endsWith(".$it") }) return UrlCheck.Rejected(FetchError.UNSUPPORTED_SITE)

        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.split('&')?.filterNot { isTracking(it) }?.joinToString("&").orEmpty()
        val canonical = "https://$host${path.trimEnd('/')}" + if (query.isEmpty()) "" else "?$query"
        val target = path.ifEmpty { "/" } + if (query.isEmpty()) "" else "?$query"
        return UrlCheck.Ok(CheckedUrl(host, target, canonical))
    }

    /** A dotted name whose last label has a letter: this rules out IP literals, including decimal and hex spellings. */
    private fun isDnsName(host: String): Boolean =
        hostPattern.matches(host) && host.substringAfterLast('.').any { it.isLetter() }

    private fun isTracking(param: String): Boolean {
        val name = param.substringBefore('=').lowercase()
        return name.startsWith("utm_") || name == "gclid" || name == "fbclid"
    }
}
