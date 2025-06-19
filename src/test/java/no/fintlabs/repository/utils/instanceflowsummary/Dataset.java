package no.fintlabs.repository.utils.instanceflowsummary;

import no.fintlabs.model.event.EventType;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

public class Dataset {

    public static final String SA_INTEGRATION_ID_1 = "testSourceApplicationIntegrationId1";
    public static final String SA_INTEGRATION_ID_2 = "testSourceApplicationIntegrationId2";
    public static final String SA_INTEGRATION_ID_3 = "testSourceApplicationIntegrationId3";
    public static final String SA_INTEGRATION_ID_4 = "testSourceApplicationIntegrationId4";

    public static final String SA_INSTANCE_ID_1 = "testSourceApplicationInstanceId1";
    public static final String SA_INSTANCE_ID_2 = "testSourceApplicationInstanceId2";
    public static final String SA_INSTANCE_ID_3 = "testSourceApplicationInstanceId3";
    public static final String SA_INSTANCE_ID_4 = "testSourceApplicationInstanceId4";

    public static final String DESTINATION_INSTANCE_ID_1 = "testDestinationInstanceId1";
    public static final String DESTINATION_INSTANCE_ID_2 = "testDestinationInstanceId2";

    public static final String STATUS_EVENT_NAME_1 = "testStatusEventName1";
    public static final String STATUS_EVENT_NAME_2 = "testStatusEventName2";
    public static final String STATUS_EVENT_NAME_3 = "testStatusEventName3";
    public static final String STATUS_EVENT_NAME_4 = "testStatusEventName4";
    public static final String STATUS_EVENT_NAME_5 = "testStatusEventName5";
    public static final List<String> ALL_STATUS_EVENT_NAMES = List.of(
            STATUS_EVENT_NAME_1, STATUS_EVENT_NAME_2, STATUS_EVENT_NAME_3, STATUS_EVENT_NAME_4, STATUS_EVENT_NAME_5
    );
    public static final String STORAGE_STATUS_EVENT_NAME_1 = "testStorageStatusEventName1";
    public static final String STORAGE_STATUS_EVENT_NAME_2 = "testStorageStatusEventName2";
    public static final List<String> ALL_STORAGE_STATUS_EVENT_NAMES = List.of(
            STORAGE_STATUS_EVENT_NAME_1, STORAGE_STATUS_EVENT_NAME_2
    );
    public static final long UNUSED_LONG_ID = -1L;
    public static final String UNUSED_STRING_ID = "testNotUsedId";

    public static final EventEntitiesAndExpectedSummary SA1_1_1 = new EventEntitiesAndExpectedSummary(
            List.of(
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_1)
                                    .integrationId(101L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ),
            InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                    .sourceApplicationInstanceId(SA_INSTANCE_ID_1)
                    .integrationId(101L)
                    .latestInstanceId(null)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC))
                    .latestStatusEventName(STATUS_EVENT_NAME_1)
                    .latestStorageStatusEventName(null)
                    .destinationInstanceIds(null)
                    .build()
    );
    public static final EventEntitiesAndExpectedSummary SA1_1_2 = new EventEntitiesAndExpectedSummary(
            List.of(
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_2)
                                    .integrationId(101L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_2)
                                    .integrationId(101L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_2)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 10, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_2)
                                    .integrationId(101L)
                                    .instanceId(1001L)
                                    .build())
                            .name(STORAGE_STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 45, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ),
            InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_1)
                    .sourceApplicationInstanceId(SA_INSTANCE_ID_2)
                    .integrationId(101L)
                    .latestInstanceId(null)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 13, 10, 0, 0, ZoneOffset.UTC))
                    .latestStatusEventName(STATUS_EVENT_NAME_2)
                    .latestStorageStatusEventName(STORAGE_STATUS_EVENT_NAME_1)
                    .destinationInstanceIds(null)
                    .build()
    );
    public static final EventEntitiesAndExpectedSummary SA2_2_1 = new EventEntitiesAndExpectedSummary(
            List.of(
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_2)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_1)
                                    .integrationId(102L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_2)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_1)
                                    .integrationId(102L)
                                    .instanceId(1002L)
                                    .build())
                            .name(STATUS_EVENT_NAME_2)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ),
            InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(2L)
                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_2)
                    .sourceApplicationInstanceId(SA_INSTANCE_ID_1)
                    .integrationId(102L)
                    .latestInstanceId(1002L)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC))
                    .latestStatusEventName(STATUS_EVENT_NAME_2)
                    .latestStorageStatusEventName(null)
                    .destinationInstanceIds(null)
                    .build()
    );
    public static final EventEntitiesAndExpectedSummary SA2_3_3 = new EventEntitiesAndExpectedSummary(
            List.of(
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_3)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_3)
                                    .integrationId(103L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_3)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_3)
                                    .integrationId(103L)
                                    .instanceId(1003L)
                                    .build())
                            .name(STATUS_EVENT_NAME_2)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_3)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_3)
                                    .integrationId(103L)
                                    .instanceId(1003L)
                                    .build())
                            .name(STORAGE_STATUS_EVENT_NAME_2)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 15, 10, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(2L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_3)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_3)
                                    .integrationId(103L)
                                    .instanceId(1003L)
                                    .archiveInstanceId(DESTINATION_INSTANCE_ID_1)
                                    .build())
                            .name(STATUS_EVENT_NAME_3)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 11, 15, 20, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ),
            InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(2L)
                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_3)
                    .sourceApplicationInstanceId(SA_INSTANCE_ID_3)
                    .integrationId(103L)
                    .latestInstanceId(1003L)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 1, 11, 15, 20, 0, ZoneOffset.UTC))
                    .latestStatusEventName(STATUS_EVENT_NAME_3)
                    .latestStorageStatusEventName(STORAGE_STATUS_EVENT_NAME_2)
                    .destinationInstanceIds(DESTINATION_INSTANCE_ID_1)
                    .build()
    );
    public static final EventEntitiesAndExpectedSummary SA3_4_4 = new EventEntitiesAndExpectedSummary(
            List.of(
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(3L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                                    .integrationId(104L)
                                    .instanceId(null)
                                    .build())
                            .name(STATUS_EVENT_NAME_1)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 7, 50, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(3L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                                    .integrationId(104L)
                                    .instanceId(null)
                                    .build())
                            .name(STORAGE_STATUS_EVENT_NAME_2)
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 7, 55, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(3L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                                    .integrationId(104L)
                                    .instanceId(1004L)
                                    .archiveInstanceId(DESTINATION_INSTANCE_ID_1)
                                    .build())
                            .name(STATUS_EVENT_NAME_5)
                            .timestamp(OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(3L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                                    .integrationId(104L)
                                    .instanceId(1004L)
                                    .archiveInstanceId(DESTINATION_INSTANCE_ID_2)
                                    .build())
                            .name(STATUS_EVENT_NAME_5)
                            .timestamp(OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 1000, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity
                            .builder()
                            .instanceFlowHeaders(InstanceFlowHeadersEmbeddable
                                    .builder()
                                    .sourceApplicationId(3L)
                                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                                    .integrationId(104L)
                                    .instanceId(1004L)
                                    .archiveInstanceId(DESTINATION_INSTANCE_ID_2)
                                    .build())
                            .name(STATUS_EVENT_NAME_5)
                            .timestamp(OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ),
            InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(3L)
                    .sourceApplicationIntegrationId(SA_INTEGRATION_ID_4)
                    .sourceApplicationInstanceId(SA_INSTANCE_ID_4)
                    .integrationId(104L)
                    .latestInstanceId(1004L)
                    .latestUpdate(OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC))
                    .latestStatusEventName(STATUS_EVENT_NAME_5)
                    .latestStorageStatusEventName(STORAGE_STATUS_EVENT_NAME_2)
                    .destinationInstanceIds(DESTINATION_INSTANCE_ID_2 + ", " + DESTINATION_INSTANCE_ID_1)
                    .build()
    );
    public static final Set<EventEntitiesAndExpectedSummary> ALL_EVENTS_AND_EXPECTED_SUMMARIES = Set.of(
            SA1_1_1,
            SA1_1_2,
            SA2_2_1,
            SA2_3_3,
            SA3_4_4
    );
}
