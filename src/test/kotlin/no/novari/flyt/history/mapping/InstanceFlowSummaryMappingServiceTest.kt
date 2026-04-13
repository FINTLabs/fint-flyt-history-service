package no.novari.flyt.history.mapping

import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.instance.InstanceFlowSummary
import no.novari.flyt.history.model.instance.InstanceStatus
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset

class InstanceFlowSummaryMappingServiceTest {
    private lateinit var eventCategorizationService: EventCategorizationService
    private lateinit var instanceFlowSummaryMappingService: InstanceFlowSummaryMappingService

    @BeforeEach
    fun setup() {
        eventCategorizationService = mock()
        instanceFlowSummaryMappingService = InstanceFlowSummaryMappingService(eventCategorizationService)
    }

    @Test
    fun `given null instance flow summary projection when to instance flow summary then throw exception`() {
        assertThrows<IllegalArgumentException> {
            instanceFlowSummaryMappingService.toInstanceFlowSummary(null)
        }
        verifyNoMoreInteractions(eventCategorizationService)
    }

    @Test
    fun `given empty projection when to instance flow summary then return empty summary`() {
        val instanceFlowSummary =
            instanceFlowSummaryMappingService.toInstanceFlowSummary(
                InstanceFlowSummaryProjection.builder().build(),
            )

        verifyNoMoreInteractions(eventCategorizationService)
        assertThat(instanceFlowSummary).hasAllNullFieldsOrPropertiesExcept("intermediateStorageStatus")
        assertThat(instanceFlowSummary.intermediateStorageStatus).isEqualTo(InstanceStorageStatus.NEVER_STORED)
    }

    @Test
    fun `given projection with values when to instance flow summary then return mapped summary`() {
        whenever(eventCategorizationService.getStatusByEventName("testStatusEventName"))
            .thenReturn(InstanceStatus.IN_PROGRESS)
        whenever(eventCategorizationService.getStorageStatusByEventName("testStorageStatusEventName"))
            .thenReturn(InstanceStorageStatus.STORED)

        val instanceFlowSummary =
            instanceFlowSummaryMappingService.toInstanceFlowSummary(
                InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId")
                    .sourceApplicationInstanceId("testSourceApplicationInstanceId")
                    .integrationId(10L)
                    .latestInstanceId(100L)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                    .latestStatusEventName("testStatusEventName")
                    .latestStorageStatusEventName("testStorageStatusEventName")
                    .destinationInstanceIds("testDestinationId")
                    .build(),
            )

        verify(eventCategorizationService).getStatusByEventName("testStatusEventName")
        verify(eventCategorizationService).getStorageStatusByEventName("testStorageStatusEventName")
        verifyNoMoreInteractions(eventCategorizationService)

        assertThat(instanceFlowSummary)
            .isEqualTo(
                InstanceFlowSummary
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId")
                    .sourceApplicationInstanceId("testSourceApplicationInstanceId")
                    .integrationId(10L)
                    .latestInstanceId(100L)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                    .status(InstanceStatus.IN_PROGRESS)
                    .intermediateStorageStatus(InstanceStorageStatus.STORED)
                    .destinationInstanceIds("testDestinationId")
                    .build(),
            )
    }
}
