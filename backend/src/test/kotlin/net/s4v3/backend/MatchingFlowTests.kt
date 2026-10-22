package net.s4v3.backend

import net.s4v3.backend.matching.internal.MatchEvaluator
import net.s4v3.backend.matching.internal.MatchResult
import net.s4v3.backend.matching.internal.MatchStore
import net.s4v3.backend.matching.internal.MatchingSettings
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.profile.OwnedProfile
import net.s4v3.backend.profile.ProfileDirectory
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.StoredProfile
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class MatchingFlowTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val profiles: ProfileService,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val store: MatchStore,
    @Autowired private val evaluator: MatchEvaluator,
    @Autowired private val directory: ProfileDirectory,
    @Autowired private val postings: PostingQuery,
    @Autowired private val settings: MatchingSettings,
) {

    private val support = MatchingTestSupport(jdbc, profiles, ingest)

    private fun result(passed: Boolean, score: Int) = MatchResult(passed, score, listOf("ROLE_MATCH"))

    @Test
    fun `one row per pair holds the latest result`() {
        val sub = support.newUser()
        val posting = support.newPosting()

        store.upsert(sub, posting, result(true, 40), 1)
        store.upsert(sub, posting, result(false, 10), 2)

        assertEquals(1, support.count(sub))
        assertEquals(false, support.match(sub, posting)!!["passed"])
        assertEquals(2L, support.match(sub, posting)!!["profile_version"])
    }

    @Test
    fun `passing results are ordered by score then newest posting, paged, and failures are not listed`() {
        val sub = support.newUser()
        val older = support.newPosting(title = "Older").also { Thread.sleep(20) }
        val newer = support.newPosting(title = "Newer")
        val best = support.newPosting(title = "Best")
        val rejected = support.newPosting(title = "Rejected")
        store.upsert(sub, older, result(true, 70), 1)
        store.upsert(sub, newer, result(true, 70), 1)
        store.upsert(sub, best, result(true, 90), 1)
        store.upsert(sub, rejected, result(false, 99), 1)

        assertEquals(listOf(best, newer, older), store.passing(sub, 0, 20).map { it.postingId })
        assertEquals(listOf(best), store.passing(sub, 0, 1).map { it.postingId })
        assertEquals(listOf(newer), store.passing(sub, 1, 1).map { it.postingId })
    }

    @Test
    fun `a new posting is evaluated for every user with a profile, and redelivery changes nothing`() {
        val zurich = support.newUser().also { support.saveProfile(it, cantons = listOf("ZH")) }
        val geneva = support.newUser().also { support.saveProfile(it, cantons = listOf("GE")) }
        val noProfile = support.newUser()

        val posting = support.newPosting(canton = "ZH")
        MatchingTestSupport.await("both results") { support.match(zurich, posting) != null && support.match(geneva, posting) != null }

        assertEquals(true, support.match(zurich, posting)!!["passed"])
        assertEquals(false, support.match(geneva, posting)!!["passed"])
        assertNull(support.match(noProfile, posting))
        evaluator.evaluatePosting(posting)
        assertEquals(1, jdbc.queryForObject("select count(*) from posting_match where user_sub = ? and posting_id = ?", Int::class.java, zurich, posting))
    }

    @Test
    fun `one user failing does not stop the others`() {
        val good = support.newUser().also { support.saveProfile(it) }
        val goodStored = profiles.get(good)
        val ghost = "ghost-" + UUID.randomUUID() // no app_user row: storing its result fails on the foreign key
        val fake = object : ProfileDirectory {
            override fun page(afterSub: String?, size: Int) =
                if (afterSub == null) listOf(OwnedProfile(ghost, StoredProfile(1, goodStored.profile)), OwnedProfile(good, goodStored)) else emptyList()
        }
        val posting = support.newPosting()
        jdbc.update("delete from posting_match where posting_id = ?", posting)

        MatchEvaluator(profiles, fake, postings, store, settings).evaluatePosting(posting)

        assertNotNull(support.match(good, posting))
    }

    @Test
    fun `a saved profile re-evaluates all postings for that user only`() {
        val zh = support.newPosting(canton = "ZH")
        val ge = support.newPosting(canton = "GE")
        val user = support.newUser()
        val other = support.newUser().also { support.saveProfile(it, cantons = listOf("ZH")) }
        MatchingTestSupport.await("other user's results") { support.match(other, zh) != null && support.match(other, ge) != null }
        val otherBefore = support.match(other, ge)

        support.saveProfile(user, cantons = listOf("GE"))
        MatchingTestSupport.await("the user's results") { support.match(user, zh) != null && support.match(user, ge) != null }

        assertEquals(false, support.match(user, zh)!!["passed"])
        assertEquals(true, support.match(user, ge)!!["passed"])
        assertEquals(otherBefore, support.match(other, ge))
    }

    @Test
    fun `a newer save replaces the results of the older version`() {
        val posting = support.newPosting(canton = "ZH")
        val user = support.newUser()
        val v1 = support.saveProfile(user, cantons = listOf("GE"))
        support.saveProfile(user, version = v1, cantons = listOf("ZH"))

        MatchingTestSupport.await("results of version 2") { support.match(user, posting)?.get("profile_version") == 2L }

        assertEquals(true, support.match(user, posting)!!["passed"])
    }

    @Test
    fun `no result is stored for a user without a profile`() {
        val user = support.newUser()

        evaluator.evaluateProfile(user)

        assertEquals(0, support.count(user))
        assertFalse(directory.page(null, 100).any { it.sub == user })
        assertTrue(Instant.now().isAfter(Instant.EPOCH))
    }
}
