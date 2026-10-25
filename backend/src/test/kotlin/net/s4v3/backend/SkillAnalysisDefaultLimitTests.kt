package net.s4v3.backend

import net.s4v3.backend.llm.AskedSkill
import net.s4v3.backend.llm.JobText
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.JobData
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** The default limit of 20 analyses per hour: the 21st answers 429. */
@ApiTest
@Import(SkillAnalysisDefaultLimitTests.Config::class)
internal class SkillAnalysisDefaultLimitTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val applications: ApplicationService,
) {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun noSkills(): SkillExtractor = object : SkillExtractor {
            override fun extract(job: JobText, savedSkillNames: List<String>): List<AskedSkill> = emptyList()
        }
    }

    @Test
    fun `the 21st analysis within an hour answers 429`() {
        val sub = "skills-limit-" + UUID.randomUUID()
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + SharedMockIssuer.instance.token(sub))).andExpect(status().isOk)
        val app = applications.trackImported(sub, JobData("https://jobs.example.com/" + UUID.randomUUID(), "Engineer", "Employer1 AG", "A job text.", "en")).application.id
        fun analyse() = mvc.perform(post("/api/applications/$app/skills/analyse").header("Authorization", "Bearer " + SharedMockIssuer.instance.token(sub)))
        repeat(20) { analyse().andExpect(status().isOk) }
        analyse().andExpect(status().isTooManyRequests)
    }
}
