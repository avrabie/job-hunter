package net.s4v3.backend.web.internal

import net.s4v3.backend.accounts.UserHourlyLimiter
import org.springframework.stereotype.Component

/** At most `perHour` project analyses per user in any rolling hour. */
@Component
internal class AnalysisLimiter(settings: AnalysisSettings) : UserHourlyLimiter({ settings.perHour })
