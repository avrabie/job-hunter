package net.s4v3.backend.sources.internal.greenhouse

/** A Swiss place found in a job's location text. [city] and [canton] are null when only the country is named. */
internal data class SwissPlace(val city: String?, val canton: String?)

/** Decides from a job's free-text location whether it is in Switzerland, and where. */
internal object SwissLocation {

    private fun words(vararg names: String): Regex =
        Regex("(?<![\\p{L}])(?:" + names.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}])", RegexOption.IGNORE_CASE)

    private val country = words("switzerland", "schweiz", "suisse", "svizzera")

    private val cities: List<Triple<String, String, Regex>> = listOf(
        Triple("Zurich", "ZH", words("zurich", "zürich", "zuerich")),
        Triple("Winterthur", "ZH", words("winterthur")),
        Triple("Dübendorf", "ZH", words("dübendorf", "duebendorf", "dubendorf")),
        Triple("Schlieren", "ZH", words("schlieren")),
        Triple("Wallisellen", "ZH", words("wallisellen")),
        Triple("Geneva", "GE", words("geneva", "genève", "geneve", "genf")),
        Triple("Basel", "BS", words("basel", "bâle")),
        Triple("Bern", "BE", words("bern")),
        Triple("Biel", "BE", words("biel", "bienne")),
        Triple("Thun", "BE", words("thun")),
        Triple("Lausanne", "VD", words("lausanne")),
        Triple("Renens", "VD", words("renens")),
        Triple("Vevey", "VD", words("vevey")),
        Triple("Zug", "ZG", words("zug")),
        Triple("Baar", "ZG", words("baar")),
        Triple("Rotkreuz", "ZG", words("rotkreuz")),
        Triple("Lucerne", "LU", words("lucerne", "luzern")),
        Triple("St. Gallen", "SG", words("st. gallen", "st gallen", "sankt gallen")),
        Triple("Lugano", "TI", words("lugano")),
        Triple("Neuchâtel", "NE", words("neuchâtel", "neuchatel")),
        Triple("Fribourg", "FR", words("fribourg", "freiburg")),
        Triple("Schaffhausen", "SH", words("schaffhausen")),
        Triple("Chur", "GR", words("chur")),
        Triple("Aarau", "AG", words("aarau")),
    )

    /** The place for [location], or null when the text names no Swiss place. The earliest known city in the text wins. */
    fun find(location: String?): SwissPlace? {
        if (location.isNullOrBlank()) return null
        val first = cities
            .mapNotNull { (city, canton, regex) -> regex.find(location)?.let { Triple(city, canton, it.range.first) } }
            .minByOrNull { it.third }
        if (first != null) return SwissPlace(first.first, first.second)
        return if (country.containsMatchIn(location)) SwissPlace(null, null) else null
    }
}

/** Decides from a job's title whether it is an IT role: some keyword as a whole word and no excluded one. */
internal class RoleFilter(keywords: List<String>, excluded: List<String>) {

    private val include = pattern(keywords)
    private val exclude = pattern(excluded)

    fun isIt(title: String): Boolean =
        include != null && include.containsMatchIn(title) && exclude?.containsMatchIn(title) != true

    private fun pattern(words: List<String>): Regex? {
        val clean = words.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        return Regex(
            "(?<![\\p{L}\\p{N}])(?:" + clean.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
    }
}
