package net.s4v3.backend

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.Skill
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** Builds users, profiles and postings for the matching tests; every name is unique so tests do not meet each other. */
internal class MatchingTestSupport(
    private val jdbc: JdbcTemplate,
    private val profiles: ProfileService,
    private val ingest: PostingIngest,
) {

    fun newUser(): String = ("match-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    fun saveProfile(sub: String, version: Long = 0, cantons: List<String> = emptyList(), role: String = "Backend Engineer", skills: List<String> = listOf("Kotlin")): Long {
        val profile = Profile(preferences = Preferences(targetRoles = listOf(role), cantons = cantons), skills = skills.map { Skill(it) })
        return profiles.replace(sub, ProfileUpdate(version, profile)).version
    }

    /** An English posting that the language detector accepts; returns its id. */
    fun newPosting(title: String = "Backend Engineer", canton: String? = "ZH", company: String = "Matchco " + UUID.randomUUID()): UUID =
        ingest.ingest(
            PostingCandidate(
                sourceId = "match-src-" + UUID.randomUUID(), sourcePostingId = "1", url = "https://example.org/" + UUID.randomUUID(),
                title = title, company = company, rawPayload = "{}", city = "Zurich", canton = canton,
                description = "We are looking for an experienced engineer to build and run reliable services with Kotlin and PostgreSQL. " +
                    "You will work in a small team, review code, and improve our delivery pipeline every week.",
            ),
        ).postingId

    fun match(sub: String, posting: UUID): Map<String, Any?>? =
        jdbc.queryForList("select passed, score, reasons::text as reasons, profile_version from posting_match where user_sub = ? and posting_id = ?", sub, posting)
            .firstOrNull()

    fun count(sub: String): Int = jdbc.queryForObject("select count(*) from posting_match where user_sub = ?", Int::class.java, sub)!!

    companion object {
        /** Polls until [check] holds or ten seconds pass; the listeners run asynchronously. */
        fun await(what: String, check: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 10_000
            while (!check()) {
                if (System.currentTimeMillis() > deadline) error("timed out waiting for $what")
                Thread.sleep(50)
            }
        }
    }
}
