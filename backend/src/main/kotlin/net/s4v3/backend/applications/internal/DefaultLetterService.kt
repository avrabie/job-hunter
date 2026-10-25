package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.GenerateLetterCommand
import net.s4v3.backend.applications.LetterComments
import net.s4v3.backend.applications.LetterDetail
import net.s4v3.backend.applications.LetterFile
import net.s4v3.backend.applications.LetterHow
import net.s4v3.backend.applications.LetterLimitException
import net.s4v3.backend.applications.LetterRateLimitedException
import net.s4v3.backend.applications.LetterSelection
import net.s4v3.backend.applications.LetterSelectionInvalidException
import net.s4v3.backend.applications.LetterService
import net.s4v3.backend.applications.LetterSummary
import net.s4v3.backend.applications.LetterTextInvalidException
import net.s4v3.backend.applications.SkillAnalysisService
import net.s4v3.backend.llm.Candidate
import net.s4v3.backend.llm.GapNote
import net.s4v3.backend.llm.JobText
import net.s4v3.backend.llm.LetterLanguage
import net.s4v3.backend.llm.LetterRequest
import net.s4v3.backend.llm.LetterWriter
import net.s4v3.backend.llm.ParagraphComment
import net.s4v3.backend.llm.PreviousLetter
import net.s4v3.backend.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The letter rules. The model call runs outside any transaction (it can take a minute); the version is numbered and the
 * cap checked again under a per-application lock when the result is stored. Logs outcome, version and duration only.
 */
@Service
internal class DefaultLetterService(
    private val repository: LetterRepository,
    private val applications: ApplicationService,
    private val profiles: ProfileService,
    private val writer: LetterWriter,
    private val skillAnalysis: SkillAnalysisService,
    private val limiter: LetterLimiter,
    private val transactions: TransactionTemplate,
    private val json: JsonMapper,
    private val clock: Clock = Clock.systemUTC(),
) : LetterService {

    private val log = LoggerFactory.getLogger(DefaultLetterService::class.java)

    override fun generate(sub: String, applicationId: UUID, command: GenerateLetterCommand): LetterDetail? {
        val job = applications.frozenJob(sub, applicationId) ?: return null
        val language = command.language?.let { l -> LetterLanguage.entries.firstOrNull { it.name == l } ?: throw LetterSelectionInvalidException() }
            ?: if (job.language.equals("de", ignoreCase = true)) LetterLanguage.DE else LetterLanguage.EN
        validate(command)
        val previousRow = command.basedOnVersion?.let { repository.find(sub, applicationId, it) ?: return null }
        if (repository.count(sub, applicationId) >= LetterService.MAX_VERSIONS) throw limitReached()

        val stored = profiles.get(sub).profile
        val selection = command.selection
        val experience = selection.experience.map { stored.experience.getOrNull(it) ?: throw LetterSelectionInvalidException() }
        val projects = selection.projects.map { stored.openSourceProjects.getOrNull(it) ?: throw LetterSelectionInvalidException() }
        val skills = selection.skills.map { stored.skills.getOrNull(it) ?: throw LetterSelectionInvalidException() }

        val comments = command.comments?.takeIf { previousRow != null && (!it.general.isNullOrBlank() || it.paragraphs.isNotEmpty()) }
        val previous = previousRow?.let {
            PreviousLetter(
                text = it.text,
                generalComment = comments?.general?.takeIf { g -> g.isNotBlank() },
                paragraphComments = comments?.paragraphs.orEmpty().map { c -> ParagraphComment(c.n, c.text) },
            )
        }
        val request = LetterRequest(
            job = JobText(job.title, job.company, job.description),
            candidate = Candidate(stored.contact.fullName.orEmpty(), experience, projects, skills),
            language = language,
            instructions = command.instructions?.takeIf { it.isNotBlank() },
            previous = previous,
            // Read here, never from the request body: only the notes of the application's current gaps.
            gapNotes = skillAnalysis.gapNotes(sub, applicationId).map { GapNote(it.skill, it.text) },
        )

        if (!limiter.tryAcquire(sub)) {
            log.info("Letter refused: hourly limit reached")
            throw LetterRateLimitedException()
        }
        val started = System.nanoTime()
        val text = try {
            writer.write(request)
        } catch (e: Exception) {
            log.info("Letter failed after {} ms", (System.nanoTime() - started) / 1_000_000)
            throw e
        }
        val how = if (previousRow != null) LetterHow.REGENERATED else LetterHow.GENERATED
        val saved = store(
            sub, applicationId,
            LetterRow(
                version = 0, how = how, language = language.name, text = text, selection = json.writeValueAsString(selection),
                instructions = request.instructions, comments = comments?.let { json.writeValueAsString(it) },
                basedOnVersion = previousRow?.version, createdAt = Instant.now(clock),
            ),
        )
        log.info("Letter created: version={}, how={}, {} ms", saved.version, how, (System.nanoTime() - started) / 1_000_000)
        return detail(saved)
    }

    override fun saveEdited(sub: String, applicationId: UUID, text: String): LetterDetail? {
        val job = applications.frozenJob(sub, applicationId) ?: return null
        if (text.isBlank() || text.length > LetterService.MAX_TEXT || HTML_TAG.containsMatchIn(text)) throw LetterTextInvalidException()
        val latest = repository.list(sub, applicationId).lastOrNull()
        val language = latest?.language ?: if (job.language.equals("de", ignoreCase = true)) "DE" else "EN"
        val saved = store(
            sub, applicationId,
            LetterRow(
                version = 0, how = LetterHow.EDITED, language = language, text = text,
                selection = json.writeValueAsString(LetterSelection()), instructions = null, comments = null,
                basedOnVersion = latest?.version, createdAt = Instant.now(clock),
            ),
        )
        log.info("Letter saved: version={}, how=EDITED", saved.version)
        return detail(saved)
    }

    override fun list(sub: String, applicationId: UUID): List<LetterSummary>? {
        applications.frozenJob(sub, applicationId) ?: return null
        return repository.list(sub, applicationId).map { LetterSummary(it.version, it.how, it.language, it.createdAt, it.basedOnVersion) }
    }

    override fun get(sub: String, applicationId: UUID, version: Int): LetterDetail? =
        repository.find(sub, applicationId, version)?.let(::detail)

    override fun file(sub: String, applicationId: UUID, version: Int): LetterFile? {
        val job = applications.frozenJob(sub, applicationId) ?: return null
        val row = repository.find(sub, applicationId, version) ?: return null
        return LetterFile(row.text.toByteArray(Charsets.UTF_8), letterFileName(job.company, job.title, version))
    }

    override fun delete(sub: String, applicationId: UUID, version: Int): Boolean =
        repository.delete(sub, applicationId, version).also { if (it) log.info("Letter removed: version={}", version) }

    /** Numbers the version and inserts it, with the cap checked once more under the lock. */
    private fun store(sub: String, applicationId: UUID, row: LetterRow): LetterRow =
        transactions.execute {
            repository.lock(sub, applicationId)
            if (repository.count(sub, applicationId) >= LetterService.MAX_VERSIONS) throw limitReached()
            val numbered = row.copy(version = repository.latestVersion(sub, applicationId) + 1)
            repository.insert(sub, applicationId, numbered)
            numbered
        }

    private fun limitReached(): LetterLimitException {
        log.info("Letter refused: version limit reached")
        return LetterLimitException()
    }

    private fun validate(command: GenerateLetterCommand) {
        val s = command.selection
        if (s.experience.size > LetterService.MAX_EXPERIENCE || s.projects.size > LetterService.MAX_PROJECTS || s.skills.size > LetterService.MAX_SKILLS) {
            throw LetterSelectionInvalidException()
        }
        if (listOf(s.experience, s.projects, s.skills).any { it.toSet().size != it.size }) throw LetterSelectionInvalidException()
        if ((command.instructions?.length ?: 0) > LetterService.MAX_INSTRUCTIONS) throw LetterSelectionInvalidException()
        val c = command.comments
        if (c != null) {
            if ((c.general?.length ?: 0) > LetterService.MAX_COMMENT) throw LetterSelectionInvalidException()
            if (c.paragraphs.size + (if (c.general.isNullOrBlank()) 0 else 1) > LetterService.MAX_COMMENTS) throw LetterSelectionInvalidException()
            if (c.paragraphs.any { it.n < 1 || it.text.length > LetterService.MAX_COMMENT }) throw LetterSelectionInvalidException()
        }
    }

    private fun detail(row: LetterRow) = LetterDetail(
        version = row.version, how = row.how, language = row.language, createdAt = row.createdAt, basedOnVersion = row.basedOnVersion,
        text = row.text,
        selection = row.selection?.let { json.readValue(it, LetterSelection::class.java) } ?: LetterSelection(),
        instructions = row.instructions,
        comments = row.comments?.let { json.readValue(it, LetterComments::class.java) },
    )

    private companion object {
        val HTML_TAG = Regex("<\\s*/?\\s*[A-Za-z!]")
    }
}
