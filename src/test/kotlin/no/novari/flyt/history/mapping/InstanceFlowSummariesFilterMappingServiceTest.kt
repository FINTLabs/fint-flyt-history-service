package no.novari.flyt.history.mapping

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.instance.InstanceStatus
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.history.model.time.TimeFilter
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.InstanceStorageStatusQueryFilter
import no.novari.flyt.history.repository.filters.TimeQueryFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.ZoneId

class InstanceFlowSummariesFilterMappingServiceTest {
    private lateinit var validator: Validator
    private lateinit var eventCategorizationService: EventCategorizationService
    private lateinit var timeFilterMappingService: TimeFilterMappingService
    private lateinit var instanceFlowSummariesFilterMappingService: InstanceFlowSummariesFilterMappingService

    @BeforeEach
    fun setup() {
        validator = mock()
        eventCategorizationService = mock()
        timeFilterMappingService = mock()
        instanceFlowSummariesFilterMappingService =
            InstanceFlowSummariesFilterMappingService(
                validator,
                eventCategorizationService,
                timeFilterMappingService,
            )
    }

    @Test
    fun `given null instance flow summaries filter when to query filter then throw exception`() {
        assertThrows<IllegalArgumentException> {
            instanceFlowSummariesFilterMappingService.toQueryFilter(null)
        }
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)
    }

    @Test
    fun `given validation error on instance flow summaries filter when to query filter then throw exception`() {
        val instanceFlowSummariesFilter = InstanceFlowSummariesFilter.builder().build()
        whenever(
            validator.validate(instanceFlowSummariesFilter),
        ).thenReturn(setOf(mock<ConstraintViolation<InstanceFlowSummariesFilter>>()))

        assertThrows<IllegalArgumentException> {
            instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter)
        }

        verify(validator, times(1)).validate(instanceFlowSummariesFilter)
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)
    }

    @Test
    fun `given empty instance flow summaries filter when to query filter then return empty query filter`() {
        val instanceFlowSummariesFilter = InstanceFlowSummariesFilter.builder().build()
        whenever(validator.validate(instanceFlowSummariesFilter)).thenReturn(emptySet())

        val queryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter,
            )

        verify(validator, times(1)).validate(instanceFlowSummariesFilter)
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)
        assertThat(queryFilter).isEqualTo(InstanceFlowSummariesQueryFilter.builder().build())
    }

    @Test
    fun `given instance flow summaries filter with values when to query filter then return query filter with values`() {
        val timeFilter: TimeFilter = mock()
        val timeQueryFilter: TimeQueryFilter = mock()

        val instanceFlowSummariesFilter =
            InstanceFlowSummariesFilter
                .builder()
                .time(timeFilter)
                .sourceApplicationIds(listOf(1L, 2L))
                .sourceApplicationIntegrationIds(
                    listOf(
                        "testSourceApplicationIntegrationId1",
                        "testSourceApplicationIntegrationId2",
                    ),
                ).sourceApplicationInstanceIds(
                    listOf(
                        "testSourceApplicationInstanceId1",
                        "testSourceApplicationInstanceId2",
                    ),
                ).integrationIds(listOf(10L, 11L))
                .statuses(listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED))
                .storageStatuses(listOf(InstanceStorageStatus.STORED, InstanceStorageStatus.NEVER_STORED))
                .associatedEvents(
                    listOf(
                        EventCategory.INSTANCE_REGISTERED,
                        EventCategory.INSTANCE_RECEIVAL_ERROR,
                    ),
                ).destinationIds(listOf("testDestinationId1", "testDestinationId2"))
                .build()

        whenever(validator.validate(instanceFlowSummariesFilter)).thenReturn(emptySet())
        whenever(
            eventCategorizationService.getEventNamesByInstanceStatuses(
                listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED),
            ),
        ).thenReturn(setOf("testStatusName1", "testStatusName2"))
        whenever(
            eventCategorizationService.getEventNamesByInstanceStorageStatuses(
                listOf(InstanceStorageStatus.STORED, InstanceStorageStatus.NEVER_STORED),
            ),
        ).thenReturn(setOf("testStorageStatusName1"))
        whenever(timeFilterMappingService.toQueryFilter(timeFilter, ZoneId.of("Europe/Oslo")))
            .thenReturn(timeQueryFilter)

        val queryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter,
            )

        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStatuses(
            listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED),
        )
        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStorageStatuses(
            listOf(InstanceStorageStatus.STORED, InstanceStorageStatus.NEVER_STORED),
        )
        verify(timeFilterMappingService, times(1)).toQueryFilter(timeFilter, ZoneId.of("Europe/Oslo"))
        verify(validator, times(1)).validate(instanceFlowSummariesFilter)
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)

        assertThat(queryFilter)
            .isEqualTo(
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .sourceApplicationIds(listOf(1L, 2L))
                    .sourceApplicationIntegrationIds(
                        listOf(
                            "testSourceApplicationIntegrationId1",
                            "testSourceApplicationIntegrationId2",
                        ),
                    ).sourceApplicationInstanceIds(
                        listOf(
                            "testSourceApplicationInstanceId1",
                            "testSourceApplicationInstanceId2",
                        ),
                    ).integrationIds(listOf(10L, 11L))
                    .statusEventNames(setOf("testStatusName1", "testStatusName2"))
                    .instanceStorageStatusQueryFilter(
                        InstanceStorageStatusQueryFilter(
                            setOf("testStorageStatusName1"),
                            true,
                        ),
                    ).associatedEventNames(
                        listOf(
                            EventCategory.INSTANCE_REGISTERED.eventName,
                            EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                        ),
                    ).destinationIds(listOf("testDestinationId1", "testDestinationId2"))
                    .timeQueryFilter(timeQueryFilter)
                    .build(),
            )
    }

    @Test
    fun `given instance flow summaries with statuses when to query filter then return event names for statuses`() {
        val instanceFlowSummariesFilter =
            InstanceFlowSummariesFilter
                .builder()
                .statuses(listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED))
                .build()

        whenever(validator.validate(instanceFlowSummariesFilter)).thenReturn(emptySet())
        whenever(
            eventCategorizationService.getEventNamesByInstanceStatuses(
                listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED),
            ),
        ).thenReturn(setOf("testStatusName1", "testStatusName2"))

        val queryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter,
            )

        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStatuses(
            listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED),
        )
        verify(validator, times(1)).validate(instanceFlowSummariesFilter)
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)

        assertThat(queryFilter)
            .isEqualTo(
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .statusEventNames(setOf("testStatusName1", "testStatusName2"))
                    .build(),
            )
    }

    @Test
    fun `given latest status events when to query filter then return event names for statuses`() {
        val instanceFlowSummariesFilter =
            InstanceFlowSummariesFilter
                .builder()
                .latestStatusEvents(
                    listOf(
                        EventCategory.INSTANCE_DISPATCHED,
                        EventCategory.INSTANCE_DISPATCHING_ERROR,
                    ),
                ).build()

        whenever(validator.validate(instanceFlowSummariesFilter)).thenReturn(emptySet())

        val queryFilter =
            instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter,
            )

        verify(validator, times(1)).validate(instanceFlowSummariesFilter)
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService)

        assertThat(queryFilter)
            .isEqualTo(
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .statusEventNames(
                        setOf(
                            EventCategory.INSTANCE_DISPATCHED.eventName,
                            EventCategory.INSTANCE_DISPATCHING_ERROR.eventName,
                        ),
                    ).build(),
            )
    }
}
