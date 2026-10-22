package net.s4v3.backend.sources.internal

/**
 * The `User-agent: *` rules of a robots.txt file, evaluated as in RFC 9309: `Allow` and `Disallow` patterns with `*`
 * and a trailing `$`, the longest matching pattern wins and `Allow` wins a tie. Rules of other agents are ignored.
 */
internal class RobotsTxt private constructor(private val rules: List<Rule>) {

    private class Rule(val allow: Boolean, val length: Int, val regex: Regex)

    fun allows(path: String): Boolean {
        val best = rules.filter { it.regex.containsMatchIn(path) }
            .maxWithOrNull(compareBy<Rule> { it.length }.thenBy { it.allow })
        return best?.allow ?: true
    }

    companion object {
        val ALLOW_ALL = RobotsTxt(emptyList())

        fun parse(text: String): RobotsTxt {
            val rules = mutableListOf<Rule>()
            var inGroup = false
            var starGroup = false
            var lastWasAgent = false
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val field = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                when (field) {
                    "user-agent" -> {
                        if (!lastWasAgent) starGroup = false
                        if (value == "*") starGroup = true
                        inGroup = true
                        lastWasAgent = true
                    }
                    "allow", "disallow" -> {
                        lastWasAgent = false
                        if (inGroup && starGroup && value.isNotEmpty()) {
                            rules += Rule(field == "allow", value.length, toRegex(value))
                        }
                    }
                    else -> lastWasAgent = false
                }
            }
            return RobotsTxt(rules)
        }

        private fun toRegex(pattern: String): Regex {
            val anchored = pattern.endsWith("$")
            val body = if (anchored) pattern.dropLast(1) else pattern
            val regex = body.split('*').joinToString(".*") { Regex.escape(it) }
            return Regex("^$regex" + if (anchored) "$" else "")
        }
    }
}
