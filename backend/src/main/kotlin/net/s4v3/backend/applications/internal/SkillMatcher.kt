package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.EvidencedSkill
import net.s4v3.backend.applications.GapSkill
import net.s4v3.backend.applications.IncludedSkill
import net.s4v3.backend.applications.SavedSkillRef
import net.s4v3.backend.llm.AskedEvidence
import net.s4v3.backend.llm.SkillImportance

/** One skill the model said the job asks for, as stored. */
internal data class StoredAsked(val name: String = "", val importance: SkillImportance = SkillImportance.REQUIRED, val related: List<String> = emptyList(), val evidence: AskedEvidence? = null)

internal data class Matched(val include: List<IncludedSkill>, val evidenced: List<EvidencedSkill>, val gaps: List<GapSkill>, val other: List<SavedSkillRef>)

/**
 * Decides which asked-for skills the profile has. Only code decides: names are equal after lowercasing and dropping
 * punctuation and spaces (`+` and `#` stay, so C and C++ differ), or a fixed alias table says they are the same. The model
 * never decides a match; its related names count only when they are exactly saved skill names.
 */
internal object SkillMatcher {

    fun match(asked: List<StoredAsked>, saved: List<String>, notes: Map<String, String> = emptyMap(), evidence: EvidenceCheck = EvidenceCheck.NONE): Matched {
        val savedKeys = saved.map { canonical(it) }
        val matchedSaved = HashSet<Int>()
        val include = ArrayList<IncludedSkill>()
        val evidenced = ArrayList<EvidencedSkill>()
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
                val hit = evidence.check(a.name, a.evidence)
                if (hit != null) {
                    evidenced += EvidencedSkill(a.name, a.importance, hit.kind, hit.index, hit.source, hit.quote)
                    continue
                }
                val related = a.related.distinct().mapNotNull { r -> saved.indexOf(r).takeIf { it >= 0 }?.let { SavedSkillRef(r, it) } }
                gaps += GapSkill(a.name, a.importance, related, notes[a.name])
            }
        }
        // A saved skill counts as asked for when any asked name maps to it, so duplicates in the profile stay out of "other".
        val askedKeys = asked.map { canonical(it.name) }.toSet()
        val other = saved.indices.filter { it !in matchedSaved && savedKeys[it] !in askedKeys }.map { SavedSkillRef(saved[it], it) }
        return Matched(include, evidenced, gaps, other)
    }

    /** Every form (canonical) that the alias table treats as the same skill as [name], including its own. */
    fun aliasGroup(name: String): Set<String> {
        val key = canonical(name)
        return ALIAS.filterValues { it == key }.keys + key
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
