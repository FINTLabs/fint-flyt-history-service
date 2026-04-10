package no.novari.flyt.history.mapping

import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.repository.entities.EventEntity
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service

@Service
class EventMappingService(
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
    private val eventCategorizationService: EventCategorizationService,
) {
    fun toEvent(eventEntity: EventEntity?): Event {
        requireNotNull(eventEntity) { "Event entity is null" }

        return Event(
            instanceFlowHeaders =
                eventEntity.instanceFlowHeaders?.let(instanceFlowHeadersMappingService::toInstanceFlowHeaders),
            category = eventEntity.name?.let(eventCategorizationService::getCategoryByEventName),
            timestamp = eventEntity.timestamp,
            type = eventEntity.type,
            applicationId = eventEntity.applicationId,
            errors = eventEntity.errors,
        )
    }

    fun toEventPage(events: Page<EventEntity>?): Page<Event> {
        requireNotNull(events) { "events is null" }
        return events.map(::toEvent)
    }

    fun toEventEntity(event: Event?): EventEntity {
        requireNotNull(event) { "event is null" }

        return EventEntity(
            instanceFlowHeaders =
                event.instanceFlowHeaders?.let(instanceFlowHeadersMappingService::toEmbeddable),
            name = event.category?.eventName,
            timestamp = event.timestamp,
            type = event.type,
            applicationId = event.applicationId,
            errors = event.errors.toMutableList(),
        )
    }
}
