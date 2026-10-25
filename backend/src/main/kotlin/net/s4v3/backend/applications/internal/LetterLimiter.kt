package net.s4v3.backend.applications.internal

import net.s4v3.backend.accounts.UserHourlyLimiter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@EnableConfigurationProperties(LetterLimitSettings::class)
internal class LetterLimitConfig

/**
 * `jobhunter.letters.per-hour`: model calls (generate and regenerate) per user and hour. Manual edits do not count.
 * `jobhunter.letters.skills-per-hour`: skill analyses per user and hour, counted apart from the letters.
 */
@ConfigurationProperties("jobhunter.letters")
internal data class LetterLimitSettings(val perHour: Int = 20, val skillsPerHour: Int = 20)

@Component
internal class LetterLimiter(settings: LetterLimitSettings) : UserHourlyLimiter({ settings.perHour })

/** The limiter of skill analyses; a separate instance, so neither limit uses up the other. */
@Component
internal class SkillsLimiter(settings: LetterLimitSettings) : UserHourlyLimiter({ settings.skillsPerHour })
