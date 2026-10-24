package net.s4v3.backend.cv.publiccv.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/** Removes handle holds that have run out, once a day (`@EnableScheduling` is on in `sources`). Only a count is logged. */
@Component
internal class PublicCvMaintenance(
    private val repo: PublicCvRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(PublicCvMaintenance::class.java)

    @Scheduled(cron = "0 45 3 * * *")
    fun runDaily() {
        try {
            log.info("Public CV hold purge removed {} hold(s)", repo.purgeHolds(clock.instant()))
        } catch (e: Exception) {
            log.warn("Public CV hold purge failed: {}", e.javaClass.simpleName)
        }
    }
}
