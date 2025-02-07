package no.fintlabs.mapping;

import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.repository.entities.EventEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

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
        return Event.builder()
                .instanceFlowHeaders(
                        instanceFlowHeadersMappingService.toInstanceFlowHeaders(
                                eventEntity.getInstanceFlowHeaders()
                        )
                )
                .category(eventCategorizationService.getCategoryByName(eventEntity.getName()))
                .timestamp(eventEntity.getTimestamp())
                .type(eventEntity.getType())
                .applicationId(eventEntity.getApplicationId())
                .errors(eventEntity.getErrors())
                .build();
    }

    public Page<Event> toEventPage(Page<EventEntity> events) {
        return events.map(this::toEvent);
    }

}
