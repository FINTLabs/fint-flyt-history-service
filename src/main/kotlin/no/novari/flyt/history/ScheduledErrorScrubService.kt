package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ScheduledErrorScrubService(
    private val eventRepository: EventRepository,
    private val eventScrubber: EventScrubber,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @param:Value("\${novari.flyt.history-service.retention.time-to-keep-error-details-in-days}")
    private val timeToKeepErrorDetailsInDays: Long,
    @param:Value("\${novari.flyt.history-service.retention.scrub-batch-size}")
    private val scrubBatchSize: Int,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Scheduled(initialDelayString = "2m", fixedDelayString = "24h")
    fun scrubScheduled() {
        scrub()
    }

    fun scrub(): Int {
        val cutoff = OffsetDateTime.now(clock).minusDays(timeToKeepErrorDetailsInDays)
        var scrubbedTotal = 0

        while (true) {
            val scrubbed = transactionTemplate.execute { scrubOneBatch(cutoff) } ?: 0

            if (scrubbed == 0) {
                logger.info("Scheduled error scrub completed; scrubbed {} events older than {}", scrubbedTotal, cutoff)
                return scrubbedTotal
            }

            scrubbedTotal += scrubbed
        }
    }

    private fun scrubOneBatch(cutoff: OffsetDateTime): Int {
        val events =
            eventRepository
                .findUnscrubbedByTimestampBefore(cutoff, PageRequest.of(0, scrubBatchSize))
                .content

        if (events.isEmpty()) {
            return 0
        }

        eventScrubber.scrubEvents(events)
        return events.size
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ScheduledErrorScrubService::class.java)
    }
}
