package no.fintlabs.mapping;

import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.repository.entities.EventEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EventMappingService {

    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    private final EventCategorizationService eventCategorizationService;

    public EventMappingService(
            InstanceFlowHeadersMappingService instanceFlowHeadersMappingService,
            EventCategorizationService eventCategorizationService
    ) {
        this.instanceFlowHeadersMappingService = instanceFlowHeadersMappingService;
        this.eventCategorizationService = eventCategorizationService;
    }

    public Event toEvent(EventEntity eventEntity) {
        if (eventEntity == null) {
            throw new IllegalArgumentException("Event entity is null");
        }
        return Event.builder()
                .instanceFlowHeaders(
                        Optional.ofNullable(eventEntity.getInstanceFlowHeaders())
                                .map(instanceFlowHeadersMappingService::toInstanceFlowHeaders)
                                .orElse(null)
                )
                .category(
                        Optional.ofNullable(eventEntity.getName())
                                .map(eventCategorizationService::getCategoryByName)
                                .orElse(null)
                )
                .timestamp(eventEntity.getTimestamp())
                .type(eventEntity.getType())
                .applicationId(eventEntity.getApplicationId())
                .errors(eventEntity.getErrors())
                .build();
    }

    public Page<Event> toEventPage(Page<EventEntity> events) {
        if (events == null) {
            throw new IllegalArgumentException("events is null");
        }
        return events.map(this::toEvent);
    }

    public EventEntity toEventEntity(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        }
        return EventEntity
                .builder()
                .instanceFlowHeaders(
                        Optional.ofNullable(event.getInstanceFlowHeaders())
                                .map(instanceFlowHeadersMappingService::toEmbeddable)
                                .orElse(null)
                )
                .name(
                        Optional.ofNullable(event.getCategory())
                                .map(EventCategory::getEventName)
                                .orElse(null)
                )
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .applicationId(event.getApplicationId())
                .errors(event.getErrors())
                .build();
    }
}
