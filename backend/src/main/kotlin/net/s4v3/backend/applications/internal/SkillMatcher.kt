package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.GapSkill
import net.s4v3.backend.applications.IncludedSkill
import net.s4v3.backend.applications.SavedSkillRef
import net.s4v3.backend.llm.SkillImportance

/** One skill the model said the job asks for, as stored. */
internal data class StoredAsked(val name: String = "", val importance: SkillImportance = SkillImportance.REQUIRED, val related: List<String> = emptyList())

internal data class Matched(val include: List<IncludedSkill>, val gaps: List<GapSkill>, val other: List<SavedSkillRef>)

/**
 * Decides which asked-for skills the profile has. Only code decides: names are equal after lowercasing and dropping
 * punctuation and spaces (`+` and `#` stay, so C and C++ differ), or a fixed alias table says they are the same. The model
 * never decides a match; its related names count only when they are exactly saved skill names.
 */
internal object SkillMatcher {

    fun match(asked: List<StoredAsked>, saved: List<String>, notes: Map<String, String> = emptyMap()): Matched {
        val savedKeys = saved.map { canonical(it) }
        val matchedSaved = HashSet<Int>()
        val include = ArrayList<IncludedSkill>()
        val gaps = ArrayList<GapSkill>()
        val seen = HashSet<String>()
        for (a in asked) {
            val key = canonical(a.name)
            if (key.isEmpty() || !seen.add(key)) continue
            val index = savedKeys.indexOf(key)
            if (index >= 0) {
                matchedSaved += index
                include += IncludedSkill(a.name, a.importance, index, saved[index])
            } else {
                val related = a.related.distinct().mapNotNull { r -> saved.indexOf(r).takeIf { it >= 0 }?.let { SavedSkillRef(r, it) } }
                gaps += GapSkill(a.name, a.importance, related, notes[a.name])
            }
        }
        // A saved skill counts as asked for when any asked name maps to it, so duplicates in the profile stay out of "other".
        val askedKeys = asked.map { canonical(it.name) }.toSet()
        val other = saved.indices.filter { it !in matchedSaved && savedKeys[it] !in askedKeys }.map { SavedSkillRef(saved[it], it) }
        return Matched(include, gaps, other)
    }

    /** Lowercase, only letters, digits, `+` and `#`, then the alias table. */
    fun canonical(name: String): String {
        val n = name.lowercase().filter { it.isLetterOrDigit() || it == '+' || it == '#' }
        return ALIAS[n] ?: n
    }

    private val ALIAS: Map<String, String> = buildMap {
        listOf(
            listOf("postgresql", "postgres", "postgre", "psql"),
            listOf("kubernetes", "k8s", "k8"),
            listOf("javascript", "js", "ecmascript"),
            listOf("typescript", "ts"),
            listOf("nodejs", "node"),
            listOf("golang", "go"),
            listOf("c#", "csharp"),
            listOf("c++", "cpp"),
            listOf("dotnet", "net"),
            listOf("mongodb", "mongo"),
            listOf("react", "reactjs"),
            listOf("vue", "vuejs"),
            listOf("python", "py"),
            listOf("amazonwebservices", "aws"),
            listOf("googlecloudplatform", "googlecloud", "gcp"),
            listOf("microsoftazure", "azure"),
            listOf("machinelearning", "ml"),
            listOf("cicd", "continuousintegration"),
            listOf("sqlserver", "mssql", "microsoftsqlserver"),
            listOf("objectivec", "objc"),
            listOf("restapi", "restapis", "restful", "restfulapi", "restfulapis"),
        ).forEach { group -> group.forEach { put(it, group.first()) } }
    }
}
