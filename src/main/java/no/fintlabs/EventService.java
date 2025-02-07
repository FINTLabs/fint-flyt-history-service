package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.mapping.*;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.EventRepository;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {

    private final String applicationId;
    private final EventRepository eventRepository;
    private final EventMappingService eventMappingService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    private final InstanceStatusFilterMappingService instanceStatusFilterMappingService;
    private final InstanceFlowSummaryMappingService instanceFlowSummaryMappingService;
    private final IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService;
    private final EventCategorizationService eventCategorizationService;

    public EventService(
            @Value("${fint.application-id}") String applicationId,
            EventRepository eventRepository,
            EventMappingService eventMappingService,
            InstanceFlowHeadersMappingService instanceFlowHeadersMappingService,
            InstanceStatusFilterMappingService instanceStatusFilterMappingService,
            InstanceFlowSummaryMappingService instanceFlowSummaryMappingService,
            IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService,
            EventCategorizationService eventCategorizationService
    ) {
        this.applicationId = applicationId;
        this.eventRepository = eventRepository;
        this.eventMappingService = eventMappingService;
        this.instanceFlowHeadersMappingService = instanceFlowHeadersMappingService;
        this.instanceStatusFilterMappingService = instanceStatusFilterMappingService;
        this.instanceFlowSummaryMappingService = instanceFlowSummaryMappingService;
        this.integrationStatisticsFilterMappingService = integrationStatisticsFilterMappingService;
        this.eventCategorizationService = eventCategorizationService;
    }

    public Slice<InstanceFlowSummary> getInstanceFlowSummaries(
            InstanceFlowSummariesFilter instanceFlowSummariesFilter,
            Pageable pageable
    ) {
        return eventRepository.getInstanceFlowSummaries(
                        instanceStatusFilterMappingService.toQueryFilter(instanceFlowSummariesFilter),
                        eventCategorizationService.getAllInstanceStatusEventNames(),
                        eventCategorizationService.getAllInstanceStorageStatusEventNames(),
                        pageable
                )
                .map(instanceFlowSummaryMappingService::toInstanceFlowSummary);
    }

    public Page<Event> getAllEventsBySourceApplicationAggregateInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return eventMappingService.toEventPage(
                eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                        sourceApplicationId,
                        sourceApplicationIntegrationId,
                        sourceApplicationInstanceId,
                        pageable
                )
        );
    }

    public Optional<InstanceFlowHeaders> findInstanceFlowHeadersForLatestInstanceRegisteredEvent(Long instanceId) {
        return eventRepository.findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                        instanceId,
                        EventCategory.INSTANCE_REGISTERED.getEventName()
                )
                .map(EventEntity::getInstanceFlowHeaders)
                .map(instanceFlowHeadersMappingService::toInstanceFlowHeaders);
    }

    public Event addManuallyProcessedEvent(ManuallyProcessedEventAction manuallyProcessedEventAction) {
        return saveManualEvent(
                manuallyProcessedEventAction,
                EventCategory.INSTANCE_MANUALLY_PROCESSED,
                manuallyProcessedEventAction.getArchiveInstanceId()
        );
    }

    public Event addManuallyRejectedEvent(ManuallyRejectedEventAction manuallyRejectedEventAction) {
        return saveManualEvent(
                manuallyRejectedEventAction,
                EventCategory.INSTANCE_MANUALLY_REJECTED,
                null
        );
    }

    private Event saveManualEvent(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            EventCategory eventCategory,
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
        return eventMappingService.toEvent(
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
                                .name(eventCategory.getEventName())
                                .timestamp(OffsetDateTime.now())
                                .type(eventCategory.getType())
                                .applicationId(applicationId)
                                .build()
                )
        );
    }

    public Optional<String> findLatestArchiveInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        List<String> archiveInstanceIdsOrderedByTimestamp =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId(),
                        eventCategorizationService.getEventNamesPerInstanceStatus()
                );
        return archiveInstanceIdsOrderedByTimestamp.isEmpty()
                ? Optional.empty()
                : Optional.of(archiveInstanceIdsOrderedByTimestamp.get(0));
    }

    public Optional<Event> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                eventCategorizationService.getAllInstanceStatusEventNames()
        ).map(eventMappingService::toEvent);
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

    // TODO 17/01/2025 eivindmorch: Må ha optional filter dersom man allerede har valgt SA. Dersom denne velges først bør SA i filter for hele spørringen automatisk bli satt i frontend. Eller? Kanskje bare ha som tekstfelt uten dropdown?
    //   public Slice<Collection<String>> getAllDistinctSourceApplicationInstanceIds() {};

    public InstanceStatisticsProjection getStatistics(Collection<Long> sourceApplicationIds) {
        return eventRepository.getTotalStatistics(
                sourceApplicationIds,
                eventCategorizationService.getEventNamesPerInstanceStatus()
        );
    }

    public Slice<IntegrationStatisticsProjection> getIntegrationStatistics(
            IntegrationStatisticsFilter integrationStatisticsFilter,
            Pageable pageable
    ) {
        return eventRepository.getIntegrationStatistics(
                integrationStatisticsFilterMappingService.toQueryFilter(integrationStatisticsFilter),
                eventCategorizationService.getEventNamesPerInstanceStatus(),
                pageable
        );
    }

}
