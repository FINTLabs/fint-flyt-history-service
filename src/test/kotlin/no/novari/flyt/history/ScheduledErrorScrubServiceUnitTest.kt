package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ScheduledErrorScrubServiceUnitTest {
    private val eventRepository: EventRepository = mock()
    private val eventScrubber: EventScrubber = mock()
    private val transactionManager: PlatformTransactionManager =
        mock {
            on { getTransaction(any()) }.thenReturn(SimpleTransactionStatus())
        }
    private val clock = Clock.fixed(Instant.parse("2024-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val scheduledErrorScrubService =
        ScheduledErrorScrubService(
            eventRepository = eventRepository,
            eventScrubber = eventScrubber,
            transactionManager = transactionManager,
            clock = clock,
            timeToKeepErrorDetailsInDays = 60,
            scrubBatchSize = 2,
        )

    @Test
    fun `scrubs in batches until no events remain`() {
        val firstBatch = listOf(EventEntity(id = 1), EventEntity(id = 2))
        val secondBatch = listOf(EventEntity(id = 3), EventEntity(id = 4))
        val thirdBatch = listOf(EventEntity(id = 5))

        whenever(eventRepository.findUnscrubbedByTimestampBefore(any(), any()))
            .thenReturn(
                SliceImpl(firstBatch, Pageable.unpaged(), false),
                SliceImpl(secondBatch, Pageable.unpaged(), false),
                SliceImpl(thirdBatch, Pageable.unpaged(), false),
                SliceImpl(emptyList(), Pageable.unpaged(), false),
            )

        val scrubbedCount = scheduledErrorScrubService.scrub()

        assertThat(scrubbedCount).isEqualTo(5)

        val scrubbedBatches = argumentCaptor<List<EventEntity>>()
        verify(eventScrubber, times(3)).scrubEvents(scrubbedBatches.capture())
        assertThat(scrubbedBatches.allValues).containsExactly(firstBatch, secondBatch, thirdBatch)
    }

    @Test
    fun `bruker konfigurert cutoff når den henter batcher`() {
        whenever(eventRepository.findUnscrubbedByTimestampBefore(any(), any()))
            .thenReturn(SliceImpl(emptyList(), Pageable.unpaged(), false))

        scheduledErrorScrubService.scrub()

        val cutoffCaptor = argumentCaptor<OffsetDateTime>()
        verify(eventRepository).findUnscrubbedByTimestampBefore(cutoffCaptor.capture(), any())
        assertThat(cutoffCaptor.firstValue).isEqualTo(OffsetDateTime.now(clock).minusDays(60))
    }
}
