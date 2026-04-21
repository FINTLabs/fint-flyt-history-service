package no.novari.flyt.history

import no.novari.flyt.history.mapping.EventMappingService
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummariesFilterMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummaryMappingService
import no.novari.flyt.history.mapping.IntegrationStatisticsFilterMappingService
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.instance.InstanceFlowSummary
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventMappingService: EventMappingService,
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
    private val instanceFlowSummariesFilterMappingService: InstanceFlowSummariesFilterMappingService,
    private val instanceFlowSummaryMappingService: InstanceFlowSummaryMappingService,
    private val integrationStatisticsFilterMappingService: IntegrationStatisticsFilterMappingService,
    private val eventCategorizationService: EventCategorizationService,
) {
    fun save(event: Event): Event {
        return eventMappingService.toEvent(
            eventRepository.save(eventMappingService.toEventEntity(event)),
        )
    }

    fun getInstanceFlowSummariesTotalCount(instanceFlowSummariesFilter: InstanceFlowSummariesFilter): Long {
        val instanceFlowSummariesQueryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter)

        return eventRepository.getInstanceFlowSummariesTotalCount(
            instanceFlowSummariesQueryFilter,
            eventCategorizationService.allInstanceStatusEventNames,
            eventCategorizationService.allInstanceStorageStatusEventNames,
        )
    }

    fun getInstanceFlowSummaries(
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        limit: Int,
    ): List<InstanceFlowSummary> {
        val instanceFlowSummariesQueryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter)

        return eventRepository
            .getInstanceFlowSummaries(
                instanceFlowSummariesQueryFilter,
                eventCategorizationService.allInstanceStatusEventNames,
                eventCategorizationService.allInstanceStorageStatusEventNames,
                limit,
            ).map(instanceFlowSummaryMappingService::toInstanceFlowSummary)
    }

    fun getAllEventsBySourceApplicationAggregateInstanceId(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        pageable: Pageable,
    ): Page<Event> {
        val eventEntityPage =
            findEventEntityPage(
                sourceApplicationId = sourceApplicationId,
                sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                sourceApplicationInstanceId = sourceApplicationInstanceId,
                pageable = pageable,
            )

        return eventMappingService.toEventPage(
            eventEntityPage,
        )
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun findEventEntityPage(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        pageable: Pageable,
    ): Page<EventEntity> {
        return eventRepository
            .findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                sourceApplicationInstanceId,
                pageable,
            )
    }

    fun findInstanceFlowHeadersForLatestInstanceRegisteredEvent(instanceId: Long): InstanceFlowHeaders? {
        return eventRepository
            .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                instanceId,
                EventCategory.INSTANCE_REGISTERED.eventName,
            )?.let {
                instanceFlowHeadersMappingService.toInstanceFlowHeaders(requireNotNull(it.instanceFlowHeaders))
            }
    }

    fun findLatestArchiveInstanceId(
        sourceApplicationAggregateInstanceId: SourceApplicationAggregateInstanceId,
    ): String? {
        val archiveInstanceIdsOrderedByTimestamp =
            eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                requireNotNull(sourceApplicationAggregateInstanceId.sourceApplicationId),
                sourceApplicationAggregateInstanceId.sourceApplicationIntegrationId,
                requireNotNull(sourceApplicationAggregateInstanceId.sourceApplicationInstanceId),
            )

        return archiveInstanceIdsOrderedByTimestamp.firstOrNull()
    }

    fun findLatestStatusEventBySourceApplicationAggregateInstanceId(
        sourceApplicationAggregateInstanceId: SourceApplicationAggregateInstanceId,
    ): Event? {
        return eventRepository
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                eventCategorizationService.allInstanceStatusEventNames,
            )?.let(eventMappingService::toEvent)
    }

    fun getStatistics(sourceApplicationIds: Collection<Long>): InstanceStatisticsProjection {
        return eventRepository.getTotalStatistics(
            sourceApplicationIds,
            eventCategorizationService.eventNamesPerInstanceStatus,
        )
    }

    fun getStatisticsForAllSourceApplications(): InstanceStatisticsProjection {
        return eventRepository.getTotalStatisticsForAllSourceApplications(
            eventCategorizationService.eventNamesPerInstanceStatus,
        )
    }

    fun getIntegrationStatistics(
        integrationStatisticsFilter: IntegrationStatisticsFilter,
        pageable: Pageable,
    ): Slice<IntegrationStatisticsProjection> {
        return eventRepository.getIntegrationStatistics(
            integrationStatisticsFilterMappingService.toQueryFilter(integrationStatisticsFilter),
            eventCategorizationService.eventNamesPerInstanceStatus,
            pageable,
        )
    }
}
