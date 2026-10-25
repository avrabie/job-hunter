package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.SkillAnalysisService
import net.s4v3.backend.applications.SkillNoteInput
import net.s4v3.backend.applications.SkillNotesInvalidException
import net.s4v3.backend.applications.SkillsRateLimitedException
import net.s4v3.backend.applications.SkillsState
import net.s4v3.backend.llm.JobText
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The skill analysis rules. The model call runs outside any transaction. The split into include / gaps / other is derived
 * from the current saved profile on every read. Logs outcome and duration only, never a skill name, note or description.
 */
@Service
internal class DefaultSkillAnalysisService(
    private val repository: SkillAnalysisRepository,
    private val applications: ApplicationService,
    private val profiles: ProfileService,
    private val extractor: SkillExtractor,
    private val limiter: SkillsLimiter,
    private val json: JsonMapper,
    private val clock: Clock = Clock.systemUTC(),
) : SkillAnalysisService {

    private val log = LoggerFactory.getLogger(DefaultSkillAnalysisService::class.java)

    override fun get(sub: String, applicationId: UUID): SkillsState? {
        applications.frozenJob(sub, applicationId) ?: return null
        val row = repository.find(sub, applicationId) ?: return SkillsState.NotAnalysed
        return view(sub, row)
    }

    override fun analyse(sub: String, applicationId: UUID): SkillsState.Analysed? {
        val job = applications.frozenJob(sub, applicationId) ?: return null
        val saved = savedNames(sub)
        if (!limiter.tryAcquire(sub)) {
            log.info("Skill analysis refused: hourly limit reached")
            throw SkillsRateLimitedException()
        }
        val started = System.nanoTime()
        val asked = try {
            extractor.extract(JobText(job.title, job.company, job.description), saved)
        } catch (e: Exception) {
            log.info("Skill analysis failed after {} ms", (System.nanoTime() - started) / 1_000_000)
            throw e
        }
        val stored = asked.map { StoredAsked(it.name, it.importance, it.related) }
        // Notes are kept when their skill is still a gap of the new analysis (against the profile as it is now).
        val oldNotes = repository.find(sub, applicationId)?.let { readNotes(it) }.orEmpty()
        val gapNames = SkillMatcher.match(stored, saved).gaps.map { it.name }.toSet()
        val kept = oldNotes.filter { it.skill in gapNames }
        val row = SkillAnalysisRow(json.writeValueAsString(stored), json.writeValueAsString(kept), Instant.now(clock))
        repository.upsert(sub, applicationId, row)
        log.info("Skill analysis stored: {} skills, {} ms", stored.size, (System.nanoTime() - started) / 1_000_000)
        return view(sub, row)
    }

    override fun saveNotes(sub: String, applicationId: UUID, notes: List<SkillNoteInput>): SkillsState.Analysed? {
        applications.frozenJob(sub, applicationId) ?: return null
        val row = repository.find(sub, applicationId) ?: return null
        if (notes.size > SkillAnalysisService.MAX_NOTES) throw SkillNotesInvalidException()
        val gaps = SkillMatcher.match(readAsked(row), savedNames(sub)).gaps.associateBy { it.name.lowercase() }
        val seen = HashSet<String>()
        val clean = notes.map { n ->
            val text = n.text.trim()
            val gap = gaps[n.skill.trim().lowercase()] ?: throw SkillNotesInvalidException()
            if (text.isEmpty() || text.length > SkillAnalysisService.MAX_NOTE || HTML_TAG.containsMatchIn(text)) throw SkillNotesInvalidException()
            if (!seen.add(gap.name)) throw SkillNotesInvalidException()
            SkillNoteInput(gap.name, text)
        }
        val updated = row.copy(notes = json.writeValueAsString(clean))
        if (!repository.updateNotes(sub, applicationId, updated.notes)) return null
        log.info("Skill notes saved: {} notes", clean.size)
        return view(sub, updated)
    }

    override fun gapNotes(sub: String, applicationId: UUID): List<SkillNoteInput> {
        val row = repository.find(sub, applicationId) ?: return emptyList()
        return view(sub, row).gaps.mapNotNull { g -> g.note?.let { SkillNoteInput(g.name, it) } }
    }

    private fun savedNames(sub: String): List<String> = profiles.get(sub).profile.skills.map { it.name }

    private fun readAsked(row: SkillAnalysisRow): List<StoredAsked> = json.readValue(row.analysis, Array<StoredAsked>::class.java).toList()

    private fun readNotes(row: SkillAnalysisRow): List<SkillNoteInput> = json.readValue(row.notes, Array<SkillNoteInput>::class.java).toList()

    private fun view(sub: String, row: SkillAnalysisRow): SkillsState.Analysed {
        val notes = readNotes(row).associate { it.skill to it.text }
        val m = SkillMatcher.match(readAsked(row), savedNames(sub), notes)
        return SkillsState.Analysed(row.createdAt, m.include, m.gaps, m.other)
    }

    private companion object {
        val HTML_TAG = Regex("<\\s*/?\\s*[A-Za-z!]")
    }
}
