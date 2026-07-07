package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Felles scrubbing-logikk: tømmer `error.args`-verdier, markerer hendelsen som scrubbet og
 * stempler `scrubbedAt`. Persisteres via JPA slik at Envers/JPA-auditing kan fange opp endringene.
 *
 * Brukes av både [ErrorScrubService] (per instanceflow) og [ScheduledErrorScrubService]
 * (planlagt retensjons-skrubbing).
 */
@Component
class EventScrubber(
    private val eventRepository: EventRepository,
    private val clock: Clock,
) {
    fun scrubEvents(events: List<EventEntity>) {
        if (events.isEmpty()) return

        val now = OffsetDateTime.now(clock)
        events.forEach { event ->
            event.errors.forEach { error ->
                error.args = error.args?.mapValues { "" }
            }
            event.isScrubbed = true
            if (event.scrubbedAt == null) {
                event.scrubbedAt = now
            }
        }
        eventRepository.saveAll(events)
    }
}
