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
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.history.repository.filters.EventNamesPerInstanceStatus
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

class EventServiceTest {
    private lateinit var eventRepository: EventRepository
    private lateinit var eventMappingService: EventMappingService
    private lateinit var instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService
    private lateinit var instanceFlowSummariesFilterMappingService: InstanceFlowSummariesFilterMappingService
    private lateinit var instanceFlowSummaryMappingService: InstanceFlowSummaryMappingService
    private lateinit var integrationStatisticsFilterMappingService: IntegrationStatisticsFilterMappingService
    private lateinit var eventCategorizationService: EventCategorizationService
    private lateinit var eventService: EventService

    @BeforeEach
    fun setup() {
        eventRepository = mock()
        eventMappingService = mock()
        instanceFlowHeadersMappingService = mock()
        instanceFlowSummariesFilterMappingService = mock()
        instanceFlowSummaryMappingService = mock()
        integrationStatisticsFilterMappingService = mock()
        eventCategorizationService = mock()
        eventService =
            EventService(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService,
            )
    }

    @Test
    fun `when save then invoke dto to entity mapping and repository save and entity to dto mapping`() {
        val event: Event = mock()
        val eventEntity: EventEntity = mock()
        val persistedEventEntity: EventEntity = mock()
        val persistedEvent: Event = mock()
        whenever(eventMappingService.toEventEntity(event)).thenReturn(eventEntity)
        whenever(eventRepository.save(eventEntity)).thenReturn(persistedEventEntity)
        whenever(eventMappingService.toEvent(persistedEventEntity)).thenReturn(persistedEvent)

        val result = eventService.save(event)

        verify(eventMappingService, times(1)).toEventEntity(event)
        verify(eventMappingService, times(1)).toEvent(persistedEventEntity)
        verifyNoMoreInteractions(eventMappingService)
        verify(eventRepository, times(1)).save(eventEntity)
        verifyNoMoreInteractions(eventRepository)
        assertThat(result).isEqualTo(persistedEvent)
    }

    @Test
    fun `when get instance flow summaries total count invoke filter mapping categorization and repository query`() {
        val instanceFlowSummariesFilter: InstanceFlowSummariesFilter = mock()
        val instanceFlowSummariesQueryFilter: InstanceFlowSummariesQueryFilter = mock()
        whenever(instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter))
            .thenReturn(instanceFlowSummariesQueryFilter)
        whenever(eventCategorizationService.allInstanceStatusEventNames).thenReturn(setOf("testEventName1"))
        whenever(eventCategorizationService.allInstanceStorageStatusEventNames).thenReturn(setOf("testEventName2"))
        whenever(
            eventRepository.getInstanceFlowSummariesTotalCount(
                instanceFlowSummariesQueryFilter,
                setOf("testEventName1"),
                setOf("testEventName2"),
            ),
        ).thenReturn(5L)

        val instanceFlowSummariesTotalCount =
            eventService.getInstanceFlowSummariesTotalCount(instanceFlowSummariesFilter)

        verify(instanceFlowSummariesFilterMappingService, times(1)).toQueryFilter(instanceFlowSummariesFilter)
        verify(eventRepository, times(1)).getInstanceFlowSummariesTotalCount(
            instanceFlowSummariesQueryFilter,
            setOf("testEventName1"),
            setOf("testEventName2"),
        )
        verify(eventCategorizationService, times(1)).allInstanceStatusEventNames
        verify(eventCategorizationService, times(1)).allInstanceStorageStatusEventNames
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(instanceFlowSummariesTotalCount).isEqualTo(5L)
    }

    @Test
    fun `when get instance flow summaries invoke filter mapping categorization repository query and summary mapping`() {
        val instanceFlowSummariesFilter: InstanceFlowSummariesFilter = mock()
        val instanceFlowSummariesQueryFilter: InstanceFlowSummariesQueryFilter = mock()
        whenever(instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter))
            .thenReturn(instanceFlowSummariesQueryFilter)
        whenever(eventCategorizationService.allInstanceStatusEventNames).thenReturn(setOf("testEventName1"))
        whenever(eventCategorizationService.allInstanceStorageStatusEventNames).thenReturn(setOf("testEventName2"))

        val instanceFlowSummaryProjection1: InstanceFlowSummaryProjection = mock()
        val instanceFlowSummaryProjection2: InstanceFlowSummaryProjection = mock()
        whenever(
            eventRepository.getInstanceFlowSummaries(
                instanceFlowSummariesQueryFilter,
                setOf("testEventName1"),
                setOf("testEventName2"),
                10,
            ),
        ).thenReturn(listOf(instanceFlowSummaryProjection1, instanceFlowSummaryProjection2))

        val instanceFlowSummary1: InstanceFlowSummary = mock()
        val instanceFlowSummary2: InstanceFlowSummary = mock()
        whenever(instanceFlowSummaryMappingService.toInstanceFlowSummary(instanceFlowSummaryProjection1))
            .thenReturn(instanceFlowSummary1)
        whenever(instanceFlowSummaryMappingService.toInstanceFlowSummary(instanceFlowSummaryProjection2))
            .thenReturn(instanceFlowSummary2)

        val instanceFlowSummaries = eventService.getInstanceFlowSummaries(instanceFlowSummariesFilter, 10)

        verify(instanceFlowSummariesFilterMappingService, times(1)).toQueryFilter(instanceFlowSummariesFilter)
        verify(eventRepository, times(1)).getInstanceFlowSummaries(
            instanceFlowSummariesQueryFilter,
            setOf("testEventName1"),
            setOf("testEventName2"),
            10,
        )
        verify(instanceFlowSummaryMappingService, times(1)).toInstanceFlowSummary(instanceFlowSummaryProjection1)
        verify(instanceFlowSummaryMappingService, times(1)).toInstanceFlowSummary(instanceFlowSummaryProjection2)
        verify(eventCategorizationService, times(1)).allInstanceStatusEventNames
        verify(eventCategorizationService, times(1)).allInstanceStorageStatusEventNames
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(instanceFlowSummaries).isEqualTo(listOf(instanceFlowSummary1, instanceFlowSummary2))
    }

    @Test
    fun `when get all events by source application aggregate instance id invoke repository query and event mapping`() {
        val pageable: Pageable = mock()
        val eventEntityPage: Page<EventEntity> = mock()
        val eventPage: Page<Event> = mock()
        whenever(
            eventRepository
                .findAllByAggregateInstanceId(
                    1L,
                    "testSourceApplicationIntegrationId",
                    "testSourceApplicationInstanceId",
                    pageable,
                ),
        ).thenReturn(eventEntityPage)
        whenever(eventMappingService.toEventPage(eventEntityPage)).thenReturn(eventPage)

        val allEventsBySourceApplicationAggregateInstanceId =
            eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
                pageable,
            )

        verify(eventRepository, times(1))
            .findAllByAggregateInstanceId(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
                pageable,
            )
        verify(eventMappingService, times(1)).toEventPage(eventEntityPage)
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(allEventsBySourceApplicationAggregateInstanceId).isSameAs(eventPage)
    }

    @Test
    fun `given empty query result when finding latest registered event headers then return empty`() {
        whenever(
            eventRepository.findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.eventName,
            ),
        ).thenReturn(null)

        val instanceFlowHeadersForLatestInstanceRegisteredEvent =
            eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L)

        verify(eventRepository, times(1))
            .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.eventName,
            )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(instanceFlowHeadersForLatestInstanceRegisteredEvent).isNull()
    }

    @Test
    fun `given query result with value when finding latest registered event headers then return value`() {
        val eventEntity: EventEntity = mock()
        val instanceFlowHeadersEmbeddable: InstanceFlowHeadersEmbeddable = mock()
        val instanceFlowHeaders: InstanceFlowHeaders = mock()
        whenever(eventEntity.instanceFlowHeaders).thenReturn(instanceFlowHeadersEmbeddable)
        whenever(
            eventRepository.findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.eventName,
            ),
        ).thenReturn(eventEntity)
        whenever(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
            .thenReturn(instanceFlowHeaders)

        val instanceFlowHeadersForLatestInstanceRegisteredEvent =
            eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L)

        verify(eventRepository, times(1))
            .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.eventName,
            )
        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(instanceFlowHeadersEmbeddable)
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(instanceFlowHeadersForLatestInstanceRegisteredEvent).isSameAs(instanceFlowHeaders)
    }

    @Test
    fun `given empty query result when find latest archive instance id then return empty`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(
            eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            ),
        ).thenReturn(listOf())

        val latestArchiveInstanceId =
            eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId)

        verify(eventRepository, times(1))
            .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(latestArchiveInstanceId).isNull()
    }

    @Test
    fun `given query result with value when find latest archive instance id then return value`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(
            eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            ),
        ).thenReturn(listOf("testArchiveInstanceId"))

        val latestArchiveInstanceId =
            eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId)

        verify(eventRepository, times(1))
            .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(latestArchiveInstanceId).isEqualTo("testArchiveInstanceId")
    }

    @Test
    fun `given query result with multiple values when find latest archive instance id then return first value`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(
            eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            ),
        ).thenReturn(listOf("testArchiveInstanceId1", "testArchiveInstanceId2", "testArchiveInstanceId3"))

        val latestArchiveInstanceId =
            eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId)

        verify(eventRepository, times(1))
            .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
            )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(latestArchiveInstanceId).isEqualTo("testArchiveInstanceId1")
    }

    @Test
    fun `given empty query result when finding latest status event then return empty`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(eventCategorizationService.allInstanceStatusEventNames).thenReturn(setOf("statusEventName1"))
        whenever(
            eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                setOf("statusEventName1"),
            ),
        ).thenReturn(null)

        val latestStatusEventBySourceApplicationAggregateInstanceId =
            eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
            )

        verify(eventCategorizationService, times(1)).allInstanceStatusEventNames
        verify(eventRepository, times(1))
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                setOf("statusEventName1"),
            )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isNull()
    }

    @Test
    fun `given query result with event when finding latest status event then return event`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(eventCategorizationService.allInstanceStatusEventNames).thenReturn(setOf("statusEventName1"))

        val eventEntity: EventEntity = mock()
        whenever(
            eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                setOf("statusEventName1"),
            ),
        ).thenReturn(eventEntity)

        val event: Event = mock()
        whenever(eventMappingService.toEvent(eventEntity)).thenReturn(event)

        val latestStatusEventBySourceApplicationAggregateInstanceId =
            eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
            )

        verify(eventCategorizationService, times(1)).allInstanceStatusEventNames
        verify(eventRepository, times(1))
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                setOf("statusEventName1"),
            )
        verify(eventMappingService, times(1)).toEvent(eventEntity)
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEqualTo(event)
    }

    @Test
    fun `when get statistics then invoke`() {
        val sourceApplicationIds: List<Long> = mock()
        val eventNamesPerInstanceStatus: EventNamesPerInstanceStatus = mock()
        whenever(eventCategorizationService.eventNamesPerInstanceStatus).thenReturn(eventNamesPerInstanceStatus)

        val instanceStatisticsProjection: InstanceStatisticsProjection = mock()
        whenever(eventRepository.getTotalStatistics(sourceApplicationIds, eventNamesPerInstanceStatus))
            .thenReturn(instanceStatisticsProjection)

        val statistics = eventService.getStatistics(sourceApplicationIds)

        verify(eventCategorizationService, times(1)).eventNamesPerInstanceStatus
        verify(eventRepository, times(1)).getTotalStatistics(sourceApplicationIds, eventNamesPerInstanceStatus)
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(statistics).isSameAs(instanceStatisticsProjection)
    }

    @Test
    fun `when get statistics for all source applications then invoke`() {
        val eventNamesPerInstanceStatus: EventNamesPerInstanceStatus = mock()
        whenever(eventCategorizationService.eventNamesPerInstanceStatus).thenReturn(eventNamesPerInstanceStatus)

        val instanceStatisticsProjection: InstanceStatisticsProjection = mock()
        whenever(eventRepository.getTotalStatisticsForAllSourceApplications(eventNamesPerInstanceStatus))
            .thenReturn(instanceStatisticsProjection)

        val statistics = eventService.getStatisticsForAllSourceApplications()

        verify(eventCategorizationService, times(1)).eventNamesPerInstanceStatus
        verify(eventRepository, times(1)).getTotalStatisticsForAllSourceApplications(eventNamesPerInstanceStatus)
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(statistics).isSameAs(instanceStatisticsProjection)
    }

    @Test
    fun `get integration statistics`() {
        val integrationStatisticsFilter: IntegrationStatisticsFilter = mock()
        val pageable: Pageable = mock()
        val integrationStatisticsQueryFilter: IntegrationStatisticsQueryFilter = mock()
        whenever(integrationStatisticsFilterMappingService.toQueryFilter(integrationStatisticsFilter))
            .thenReturn(integrationStatisticsQueryFilter)

        val eventNamesPerInstanceStatus: EventNamesPerInstanceStatus = mock()
        whenever(eventCategorizationService.eventNamesPerInstanceStatus).thenReturn(eventNamesPerInstanceStatus)

        val slice: Slice<IntegrationStatisticsProjection> = mock()
        whenever(
            eventRepository.getIntegrationStatistics(
                integrationStatisticsQueryFilter,
                eventNamesPerInstanceStatus,
                pageable,
            ),
        ).thenReturn(slice)

        val integrationStatistics =
            eventService.getIntegrationStatistics(
                integrationStatisticsFilter,
                pageable,
            )

        verify(integrationStatisticsFilterMappingService, times(1)).toQueryFilter(integrationStatisticsFilter)
        verify(eventCategorizationService, times(1)).eventNamesPerInstanceStatus
        verify(eventRepository, times(1)).getIntegrationStatistics(
            integrationStatisticsQueryFilter,
            eventNamesPerInstanceStatus,
            pageable,
        )
        verifyNoMoreInteractions(
            eventRepository,
            eventMappingService,
            instanceFlowHeadersMappingService,
            instanceFlowSummariesFilterMappingService,
            instanceFlowSummaryMappingService,
            integrationStatisticsFilterMappingService,
            eventCategorizationService,
        )
        assertThat(integrationStatistics).isNotNull()
    }

    private fun testSourceApplicationAggregateInstanceId(): SourceApplicationAggregateInstanceId {
        return object : SourceApplicationAggregateInstanceId {
            override val sourceApplicationId = 1L

            override val sourceApplicationIntegrationId = "testSourceApplicationIntegrationId"

            override val sourceApplicationInstanceId = "testSourceApplicationInstanceId"
        }
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun EventRepository.findAllByAggregateInstanceId(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        pageable: Pageable,
    ): Page<EventEntity> {
        return findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            sourceApplicationId,
            sourceApplicationIntegrationId,
            sourceApplicationInstanceId,
            pageable,
        )
    }
}
