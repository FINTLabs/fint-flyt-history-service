package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ScheduledErrorScrubServiceUnitTest {
    private val eventRepository: EventRepository = mock()
    private val clock = Clock.fixed(Instant.parse("2024-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val scheduledErrorScrubService =
        ScheduledErrorScrubService(
            eventRepository = eventRepository,
            clock = clock,
            timeToKeepErrorDetailsInDays = 60,
            scrubBatchSize = 2,
        )

    @Test
    fun `scrubs in batches until no events remain`() {
        val cutoff = OffsetDateTime.now(clock).minusDays(60)
        whenever(eventRepository.scrubBatch(cutoff, 2)).thenReturn(2, 2, 1, 0)

        val scrubbedCount = scheduledErrorScrubService.scrub()

        assertThat(scrubbedCount).isEqualTo(5)
        verify(eventRepository, times(4)).scrubBatch(cutoff, 2)
        verifyNoMoreInteractions(eventRepository)
    }
}
