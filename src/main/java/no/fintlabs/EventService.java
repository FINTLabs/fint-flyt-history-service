package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.mapping.EventMappingService;
import no.fintlabs.mapping.InstanceFlowHeadersMappingService;
import no.fintlabs.model.Event;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.model.eventinfo.EventType;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.instance.InstanceInfo;
import no.fintlabs.model.instance.InstanceStatusFilter;
import no.fintlabs.model.statistics.InstanceStatistics;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {

    private final String applicationId;
    private final EventRepository eventRepository;
    private final EventMappingService eventMappingService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;

    public EventService(
            @Value("${fint.application-id}") String applicationId,
            EventRepository eventRepository,
            EventMappingService eventMappingService,
            InstanceFlowHeadersMappingService instanceFlowHeadersMappingService
    ) {
        this.applicationId = applicationId;
        this.eventRepository = eventRepository;
        this.eventMappingService = eventMappingService;
        this.instanceFlowHeadersMappingService = instanceFlowHeadersMappingService;
    }

    public Slice<InstanceInfo> getInstanceInfo(
            InstanceStatusFilter instanceStatusFilter,
            Pageable pageable
    ) {
        return eventRepository.getInstanceInfo(
                instanceStatusFilter,
                pageable
        );
    }

    public Page<Event> getAllEventsBySourceApplicationAggregateInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return eventMappingService.toEventPage(
                eventRepository.getAllBySourceApplicationAggregateInstanceId(
                        sourceApplicationId,
                        sourceApplicationIntegrationId,
                        sourceApplicationInstanceId,
                        pageable
                )
        );
    }

    public Optional<InstanceFlowHeaders> findInstanceFlowHeadersForLatestInstanceRegisteredEvent(Long instanceId) {
        return eventRepository.findInstanceFlowHeadersForLatestInstanceRegisteredEventWithInstanceId(instanceId)
                .map(instanceFlowHeadersMappingService::toInstanceFlowHeaders);
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

    public InstanceStatistics getStatistics(Collection<Long> sourceApplicationIds) {
        return eventRepository.getTotalStatistics(sourceApplicationIds);
    }

    public Page<IntegrationStatistics> getIntegrationStatistics(
            IntegrationStatisticsFilter integrationStatisticsFilter,
            Pageable pageable
    ) {
        return eventRepository.getIntegrationStatistics(
                integrationStatisticsFilter,
                pageable
        );
    }

}
