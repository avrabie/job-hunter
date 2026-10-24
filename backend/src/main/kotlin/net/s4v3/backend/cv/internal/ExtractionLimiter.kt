package net.s4v3.backend.cv.internal

import net.s4v3.backend.accounts.UserHourlyLimiter
import org.springframework.stereotype.Component

/** At most `extractionsPerHour` model calls per user in any rolling hour. */
@Component
internal class ExtractionLimiter(settings: CvSettings) : UserHourlyLimiter({ settings.extractionsPerHour })
