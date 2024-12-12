package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.mapping.InstanceFlowHeadersEmbeddableMapper;
import no.fintlabs.model.Event;
import no.fintlabs.model.InstanceStatus;
import no.fintlabs.model.InstanceStatusFilter;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.model.eventinfo.EventType;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.model.statistics.Statistics;
import no.fintlabs.repositories.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class EventService {

    private final String applicationId;
    private final EventRepository eventRepository;
    private final InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper;

    public EventService(
            @Value("${fint.application-id}") String applicationId,
            EventRepository eventRepository,
            InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper
    ) {
        this.applicationId = applicationId;
        this.eventRepository = eventRepository;
        this.instanceFlowHeadersEmbeddableMapper = instanceFlowHeadersEmbeddableMapper;
    }

    public Page<InstanceStatus> getInstanceStatuses(
            Collection<Long> userAuthorizationSourceApplicationIds,
            InstanceStatusFilter instanceStatusFilter,
            Pageable pageable
    ) {
        Collection<Long> intersectedAuthorizationAndFilterSourceApplicationIds =
                instanceStatusFilter.getSourceApplicationIds().map(
                        filterSourceApplicationIds -> intersectAuthorizationAndFilterSourceApplicationIds(
                                userAuthorizationSourceApplicationIds,
                                filterSourceApplicationIds
                        )
                ).orElse(userAuthorizationSourceApplicationIds);

        return eventRepository.getInstanceStatuses(
                instanceStatusFilter
                        .toBuilder()
                        .sourceApplicationIds(intersectedAuthorizationAndFilterSourceApplicationIds)
                        .build(),
                pageable
        );
    }

    public Page<Event> getAllEventsBySourceApplicationAggregateInstanceId(
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

    public Event addManuallyProcessedEvent(ManuallyProcessedEventAction manuallyProcessedEventAction) {
        return saveManualEvent(
                manuallyProcessedEventAction,
                InstanceStatusEvent.INSTANCE_MANUALLY_PROCESSED,
                manuallyProcessedEventAction.getArchiveInstanceId()
        );
    }

    public Event addManuallyRejectedEvent(ManuallyRejectedEventAction manuallyRejectedEventAction) {
        return saveManualEvent(
                manuallyRejectedEventAction,
                InstanceStatusEvent.INSTANCE_MANUALLY_REJECTED,
                null
        );
    }

    private Event saveManualEvent(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            InstanceStatusEvent instanceStatusEvent,
            String archiveInstanceId
    ) {
        Optional<Event> latestBySourceApplicationAggregateInstanceId =
                findLatestStatusEventBySourceApplicationAggregateInstanceId(sourceApplicationAggregateInstanceId);
        if (latestBySourceApplicationAggregateInstanceId.isEmpty()) {
            throw new NoPreviousStatusEventsFoundException();
        }
        Event latestStatusEvent = latestBySourceApplicationAggregateInstanceId.get();
        if (latestStatusEvent.getType() != EventType.INFO) {
            throw new LatesStatusEventNotOfTypeErrorException();
        }
        return eventToEventDto(
                eventRepository.save(
                        EventEntity.builder()
                                .instanceFlowHeaders(
                                        sourceApplicationAggregateInstanceIdToInstanceFlowHeadersEmbeddable(
                                                sourceApplicationAggregateInstanceId,
                                                latestStatusEvent.getInstanceFlowHeaders().getIntegrationId(),
                                                UUID.randomUUID(),
                                                archiveInstanceId
                                        )
                                )
                                .name(instanceStatusEvent.getName())
                                .timestamp(OffsetDateTime.now())
                                .type(instanceStatusEvent.getType())
                                .applicationId(applicationId)
                                .build()
                )
        );
    }

    public Optional<Event> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId
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

    private Page<Event> convertPageOfEventIntoPageOfEventDto(Page<EventEntity> events) {
        return events.map(this::eventToEventDto);
    }

    private Event eventToEventDto(EventEntity eventEntity) {
        return Event.builder()
                .instanceFlowHeaders(
                        instanceFlowHeadersEmbeddableMapper.toInstanceFlowHeaders(
                                eventEntity.getInstanceFlowHeaders()
                        )
                )
                .name(eventEntity.getName())
                .timestamp(eventEntity.getTimestamp())
                .type(eventEntity.getType())
                .applicationId(eventEntity.getApplicationId())
                .errors(eventEntity.getErrors())
                .build();
    }

    // TODO 04/12/2024 eivindmorch: Replace with query that doesnt include duplicate dispatches
    public Statistics getStatistics(List<Long> sourceApplicationIds) {
        return Statistics
                .builder()
                .dispatchedInstances(eventRepository.countDispatchedInstancesBySourceApplicationIds(sourceApplicationIds))
                .currentErrors(eventRepository.countCurrentInstanceErrorsBySourceApplicationIds(sourceApplicationIds))
                .build();
    }

    public Page<IntegrationStatistics> getIntegrationStatistics(
            Collection<Long> userAuthorizationSourceApplicationIds,
            IntegrationStatisticsFilter integrationStatisticsFilter,
            Pageable pageable
    ) {
        Collection<Long> intersectedAuthorizationAndFilterSourceApplicationIds =
                integrationStatisticsFilter.getSourceApplicationIds().map(
                        filterSourceApplicationIds -> intersectAuthorizationAndFilterSourceApplicationIds(
                                userAuthorizationSourceApplicationIds,
                                filterSourceApplicationIds
                        )
                ).orElse(userAuthorizationSourceApplicationIds);

        return eventRepository.getIntegrationStatistics(
                integrationStatisticsFilter
                        .toBuilder()
                        .sourceApplicationIds(intersectedAuthorizationAndFilterSourceApplicationIds)
                        .build(),
                pageable
        );
    }

    private Collection<Long> intersectAuthorizationAndFilterSourceApplicationIds(
            Collection<Long> userAuthorizationSourceApplicationIds,
            Collection<Long> filterSourceApplicationIds
    ) {
        if (filterSourceApplicationIds == null) {
            return new HashSet<>(userAuthorizationSourceApplicationIds);
        }

        Set<Long> intersection = new HashSet<>(userAuthorizationSourceApplicationIds);
        intersection.retainAll(filterSourceApplicationIds);
        return intersection;
    }

}
