package no.novari.flyt.history

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class InstanceInfoEventService(
    private val eventRepository: EventRepository,
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
) {
    @Transactional
    fun registerEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        eventCategory: EventCategory,
        timestamp: Instant,
        applicationId: String,
        actor: Actor? = null,
    ) {
        require(eventCategory.type == EventType.INFO) { "Event category must be of type INFO" }

        val event =
            EventEntity(
                instanceFlowHeaders = instanceFlowHeadersMappingService.toEmbeddable(instanceFlowHeaders),
                name = eventCategory.eventName,
                type = EventType.INFO,
                timestamp = timestamp.atOffset(ZoneOffset.UTC),
                applicationId = applicationId,
            )

        if (actor == null) {
            eventRepository.save(event)
        } else {
            ActorContext.withActor(actor) {
                eventRepository.save(event)
            }
        }
    }
}
