package no.novari.flyt.history.mapping

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.repository.entities.EventEntity
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service

@Service
class EventMappingService(
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
    private val eventCategorizationService: EventCategorizationService,
    private val actorDisplayResolver: ActorDisplayResolver,
) {
    fun toEvent(eventEntity: EventEntity?): Event {
        requireNotNull(eventEntity) { "Event entity is null" }
        return toEvent(eventEntity, actorDisplayResolver.resolveAll(actorsOf(eventEntity)))
    }

    private fun toEvent(
        eventEntity: EventEntity,
        displays: Map<Actor, String?>,
    ): Event {
        return Event(
            instanceFlowHeaders =
                eventEntity.instanceFlowHeaders?.let(instanceFlowHeadersMappingService::toInstanceFlowHeaders),
            category = eventEntity.name?.let(eventCategorizationService::getCategoryByEventName),
            timestamp = eventEntity.timestamp,
            isScrubbed = eventEntity.isScrubbed,
            type = eventEntity.type,
            applicationId = eventEntity.applicationId,
            errors = eventEntity.errors,
            createdAt = eventEntity.createdAt,
            createdBy = eventEntity.createdBy?.let { displays[it] },
            createdByActor = eventEntity.createdBy,
        )
    }

    fun toEventPage(events: Page<EventEntity>?): Page<Event> {
        requireNotNull(events) { "events is null" }
        val displays = actorDisplayResolver.resolveAll(events.content.flatMap(::actorsOf))
        return events.map { toEvent(it, displays) }
    }

    fun toEventEntity(event: Event?): EventEntity {
        requireNotNull(event) { "event is null" }

        return EventEntity(
            instanceFlowHeaders =
                event.instanceFlowHeaders?.let(instanceFlowHeadersMappingService::toEmbeddable),
            name = event.category?.eventName,
            timestamp = event.timestamp,
            isScrubbed = event.isScrubbed,
            type = event.type,
            applicationId = event.applicationId,
            errors = event.errors.toMutableList(),
        )
    }

    private fun actorsOf(eventEntity: EventEntity): List<Actor?> = listOf(eventEntity.createdBy)
}
