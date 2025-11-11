package no.novari.flyt.history.mapping;

import no.novari.flyt.history.model.event.EventCategorizationService;
import no.novari.flyt.history.model.instance.InstanceFlowSummary;
import no.novari.flyt.history.model.instance.InstanceStatus;
import no.novari.flyt.history.model.instance.InstanceStorageStatus;
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InstanceFlowSummaryMappingServiceTest {

    EventCategorizationService eventCategorizationService;
    InstanceFlowSummaryMappingService instanceFlowSummaryMappingService;

    @BeforeEach
    public void setup() {
        eventCategorizationService = mock(EventCategorizationService.class);
        instanceFlowSummaryMappingService = new InstanceFlowSummaryMappingService(eventCategorizationService);
    }

    @Test
    public void givenNullInstanceFlowSummaryProjection_whenToInstanceFlowSummary_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> instanceFlowSummaryMappingService.toInstanceFlowSummary(null)
        );
        verifyNoMoreInteractions(eventCategorizationService);
    }

    @Test
    public void givenEmptyInstanceFlowSummaryProjection_whenToInstanceFlowSummary_thenReturnEmptyInstanceFlowSummary() {
        InstanceFlowSummary instanceFlowSummary = instanceFlowSummaryMappingService.toInstanceFlowSummary(
                InstanceFlowSummaryProjection.builder().build()
        );
        verifyNoMoreInteractions(eventCategorizationService);
        assertThat(instanceFlowSummary).hasAllNullFieldsOrPropertiesExcept("intermediateStorageStatus");
        assertThat(instanceFlowSummary.getIntermediateStorageStatus()).isEqualTo(InstanceStorageStatus.NEVER_STORED);
    }

    @Test
    public void givenInstanceFlowSummaryProjectionWithValues_whenToInstanceFlowSummary_thenReturnInstanceFlowSummaryWithValues() {
        when(eventCategorizationService.getStatusByEventName("testStatusEventName"))
                .thenReturn(InstanceStatus.IN_PROGRESS);

        when(eventCategorizationService.getStorageStatusByEventName("testStorageStatusEventName"))
                .thenReturn(InstanceStorageStatus.STORED);

        InstanceFlowSummary instanceFlowSummary = instanceFlowSummaryMappingService.toInstanceFlowSummary(
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
                        .build()
        );

        verify(eventCategorizationService).getStatusByEventName("testStatusEventName");
        verify(eventCategorizationService).getStorageStatusByEventName("testStorageStatusEventName");
        verifyNoMoreInteractions(eventCategorizationService);

        assertThat(instanceFlowSummary).isEqualTo(
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
                        .build()
        );
    }

}
