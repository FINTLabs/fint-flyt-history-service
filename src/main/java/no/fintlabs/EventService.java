package no.fintlabs;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.mapping.*;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.EventRepository;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventMappingService eventMappingService;
    private final InstanceFlowSummariesFilterMappingService instanceFlowSummariesFilterMappingService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    private final InstanceFlowSummaryMappingService instanceFlowSummaryMappingService;
    private final IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService;
    private final EventCategorizationService eventCategorizationService;

    public EventService(
            EventRepository eventRepository,
            EventMappingService eventMappingService,
            InstanceFlowHeadersMappingService instanceFlowHeadersMappingService,
            InstanceFlowSummariesFilterMappingService instanceFlowSummariesFilterMappingService,
            InstanceFlowSummaryMappingService instanceFlowSummaryMappingService,
            IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService,
            EventCategorizationService eventCategorizationService
    ) {
        this.eventRepository = eventRepository;
        this.eventMappingService = eventMappingService;
        this.instanceFlowHeadersMappingService = instanceFlowHeadersMappingService;
        this.instanceFlowSummariesFilterMappingService = instanceFlowSummariesFilterMappingService;
        this.instanceFlowSummaryMappingService = instanceFlowSummaryMappingService;
        this.integrationStatisticsFilterMappingService = integrationStatisticsFilterMappingService;
        this.eventCategorizationService = eventCategorizationService;
    }

    public Event save(Event event) {
        return eventMappingService.toEvent(
                eventRepository.save(
                        eventMappingService.toEventEntity(
                                event
                        )
                )
        );
    }

    public long getInstanceFlowSummariesTotalCount(InstanceFlowSummariesFilter instanceFlowSummariesFilter) {
        InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter = instanceFlowSummariesFilterMappingService
                .toQueryFilter(instanceFlowSummariesFilter);
        return eventRepository.getInstanceFlowSummariesTotalCount(
                instanceFlowSummariesQueryFilter,
                eventCategorizationService.getAllInstanceStatusEventNames(),
                eventCategorizationService.getAllInstanceStorageStatusEventNames()
        );
    }

    public List<InstanceFlowSummary> getInstanceFlowSummaries(
            InstanceFlowSummariesFilter instanceFlowSummariesFilter,
            int limit
    ) {
        InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter = instanceFlowSummariesFilterMappingService
                .toQueryFilter(instanceFlowSummariesFilter);

        return eventRepository.getInstanceFlowSummaries(
                        instanceFlowSummariesQueryFilter,
                        eventCategorizationService.getAllInstanceStatusEventNames(),
                        eventCategorizationService.getAllInstanceStorageStatusEventNames(),
                        limit
                )
                .stream()
                .map(instanceFlowSummaryMappingService::toInstanceFlowSummary)
                .toList();
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

    public Optional<String> findLatestArchiveInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        List<String> archiveInstanceIdsOrderedByTimestamp =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                        sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId()
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
