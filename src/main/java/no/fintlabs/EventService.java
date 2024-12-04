package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.model.*;
import no.fintlabs.repositories.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static no.fintlabs.EventNames.INSTANCE_MANUALLY_PROCESSED;
import static no.fintlabs.EventNames.INSTANCE_MANUALLY_REJECTED;

@Service
public class EventService {

    private final String applicationId;
    private final EventRepository eventRepository;

    public EventService(
            @Value("${fint.application-id}") String applicationId,
            EventRepository eventRepository
    ) {
        this.applicationId = applicationId;
        this.eventRepository = eventRepository;
    }

    // TODO 04/12/2024 eivindmorch: Get instance status with filter and sort and page (instance table in frontend)

    public Page<EventDto> getAllEventsBySourceApplicationAggregateInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return convertPageOfEventIntoPageOfEventDto(
                eventRepository.getAllBySourceApplicationAggregateInstanceId(
                        sourceApplicationId,
                        sourceApplicationIntegrationId,
                        sourceApplicationInstanceId,
                        pageable
                )
        );
    }

    public EventDto addManuallyProcessedEvent(ManuallyProcessedEventDto manuallyProcessedEventDto) {
        Optional<EventDto> latestBySourceApplicationAggregateInstanceId =
                findLatestStatusEventBySourceApplicationAggregateInstanceId(manuallyProcessedEventDto);
        if (latestBySourceApplicationAggregateInstanceId.isEmpty()) {
            throw new NoPreviousStatusEventsFoundException();
        }
        EventDto latestStatusEvent = latestBySourceApplicationAggregateInstanceId.get();
        if (latestStatusEvent.getType() != EventType.INFO) {
            throw new LatesStatusEventNotOfTypeErrorException();
        }
        return eventToEventDto(
                eventRepository.save(
                        Event.builder()
                                .instanceFlowHeaders(
                                        sourceApplicationAggregateInstanceIdToInstanceFlowHeadersEmbeddable(
                                                manuallyProcessedEventDto,
                                                latestStatusEvent.getInstanceFlowHeaders().getIntegrationId(),
                                                UUID.randomUUID(),
                                                manuallyProcessedEventDto.getArchiveInstanceId()
                                        )
                                )
                                .name(INSTANCE_MANUALLY_PROCESSED)
                                .timestamp(OffsetDateTime.now())
                                .type(EventType.INFO)
                                .applicationId(applicationId)
                                .build()
                )
        );
    }

    public EventDto addManuallyRejectedEvent(ManuallyRejectedEventDto manuallyRejectedEventDto) {
        Optional<EventDto> latestBySourceApplicationAggregateInstanceId =
                findLatestStatusEventBySourceApplicationAggregateInstanceId(manuallyRejectedEventDto);
        if (latestBySourceApplicationAggregateInstanceId.isEmpty()) {
            throw new NoPreviousStatusEventsFoundException();
        }
        EventDto latestStatusEvent = latestBySourceApplicationAggregateInstanceId.get();
        if (latestStatusEvent.getType() != EventType.INFO) {
            throw new LatesStatusEventNotOfTypeErrorException();
        }
        return eventToEventDto(
                eventRepository.save(
                        Event.builder()
                                .instanceFlowHeaders(
                                        sourceApplicationAggregateInstanceIdToInstanceFlowHeadersEmbeddable(
                                                manuallyRejectedEventDto,
                                                latestStatusEvent.getInstanceFlowHeaders().getIntegrationId(),
                                                UUID.randomUUID(),
                                                null
                                        )
                                )
                                .name(INSTANCE_MANUALLY_REJECTED)
                                .timestamp(OffsetDateTime.now())
                                .type(EventType.INFO)
                                .applicationId(applicationId)
                                .build()
                )
        );
    }

    // TODO 04/12/2024 eivindmorch: Only get status events. Smaller version of instance status query
    public Optional<EventDto> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return eventRepository.
                findLatestBySourceApplicationIdAndSourceApplicationIntegrationIdAndSourceApplicationInstanceId(
                        sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId()
                );
    }

    private InstanceFlowHeadersEmbeddable sourceApplicationAggregateInstanceIdToInstanceFlowHeadersEmbeddable(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            Long integrationId,
            UUID correlationId,
            String archiveInstanceId
    ) {
        return InstanceFlowHeadersEmbeddable
                .builder()
                .sourceApplicationId(sourceApplicationAggregateInstanceId.getSourceApplicationId())
                .sourceApplicationIntegrationId(sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId())
                .integrationId(integrationId)
                .correlationId(correlationId)
                .archiveInstanceId(archiveInstanceId)
                .build();
    }

    private Page<EventDto> convertPageOfEventIntoPageOfEventDto(Page<Event> events) {
        return events.map(this::eventToEventDto);
    }

    private EventDto eventToEventDto(Event event) {
        return EventDto.builder()
                .instanceFlowHeaders(instanceFlowHeadersEmbeddableToInstanceFlowHeaders(event.getInstanceFlowHeaders()))
                .name(event.getName())
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .applicationId(event.getApplicationId())
                .errors(event.getErrors())
                .build();
    }

    private InstanceFlowHeaders instanceFlowHeadersEmbeddableToInstanceFlowHeaders(
            InstanceFlowHeadersEmbeddable instanceFlowHeadersEmbeddable
    ) {
        return InstanceFlowHeaders
                .builder()
                .sourceApplicationId(instanceFlowHeadersEmbeddable.getSourceApplicationId())
                .sourceApplicationIntegrationId(instanceFlowHeadersEmbeddable.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(instanceFlowHeadersEmbeddable.getSourceApplicationInstanceId())
                .fileIds(instanceFlowHeadersEmbeddable.getFileIds())
                .correlationId(instanceFlowHeadersEmbeddable.getCorrelationId())
                .integrationId(instanceFlowHeadersEmbeddable.getIntegrationId())
                .instanceId(instanceFlowHeadersEmbeddable.getInstanceId())
                .configurationId(instanceFlowHeadersEmbeddable.getConfigurationId())
                .archiveInstanceId(instanceFlowHeadersEmbeddable.getArchiveInstanceId())
                .build();
    }

}
