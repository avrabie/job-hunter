package net.s4v3.backend.cv.internal

import net.s4v3.backend.accounts.UserHourlyLimiter
import org.springframework.stereotype.Component

/** At most `perHour` CV exports per user in any rolling hour. */
@Component
internal class ExportLimiter(settings: ExportSettings) : UserHourlyLimiter({ settings.perHour })
