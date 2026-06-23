package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class EventScrubberTest {
    private val eventRepository: EventRepository = mock()
    private val fixedNow = Instant.parse("2026-06-22T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val eventScrubber = EventScrubber(eventRepository, clock)

    @Test
    fun `tømmer error-args, markerer scrubbed og stempler scrubbedAt`() {
        val event = event(args = mapOf("filename" to "doc.pdf", "reason" to "bad-payload"))

        eventScrubber.scrubEvents(listOf(event))

        assertThat(event.isScrubbed).isTrue()
        assertThat(event.scrubbedAt).isEqualTo(fixedNow.atOffset(ZoneOffset.UTC))
        assertThat(event.errors.single().args).containsExactlyEntriesOf(
            mapOf("filename" to "", "reason" to ""),
        )
    }

    @Test
    fun `beholder eksisterende scrubbedAt ved re-scrubbing`() {
        val existingScrubbedAt = OffsetDateTime.parse("2023-01-01T00:00:00Z")
        val event =
            event(args = mapOf("k" to "v")).apply {
                isScrubbed = true
                scrubbedAt = existingScrubbedAt
            }

        eventScrubber.scrubEvents(listOf(event))

        assertThat(event.scrubbedAt).isEqualTo(existingScrubbedAt)
    }

    @Test
    fun `lagrer alle eventene i én saveAll`() {
        val events = listOf(event(args = mapOf("k1" to "v1")), event(args = mapOf("k2" to "v2")))

        eventScrubber.scrubEvents(events)

        val captor = argumentCaptor<List<EventEntity>>()
        verify(eventRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `gjør ingenting for tom liste`() {
        eventScrubber.scrubEvents(emptyList())

        verifyNoInteractions(eventRepository)
    }

    private fun event(args: Map<String, String>): EventEntity =
        EventEntity(
            errors = mutableListOf(ErrorEntity(errorCode = "test", args = args)),
        )
}
