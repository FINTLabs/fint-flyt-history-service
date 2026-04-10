package no.novari.flyt.history.repository.utils.instanceflowsummary

import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Dataset private constructor() {
    companion object {
        const val SA_INTEGRATION_ID_1 = "testSourceApplicationIntegrationId1"
        const val SA_INTEGRATION_ID_2 = "testSourceApplicationIntegrationId2"
        const val SA_INTEGRATION_ID_3 = "testSourceApplicationIntegrationId3"
        const val SA_INTEGRATION_ID_4 = "testSourceApplicationIntegrationId4"

        const val SA_INSTANCE_ID_1 = "testSourceApplicationInstanceId1"
        const val SA_INSTANCE_ID_2 = "testSourceApplicationInstanceId2"
        const val SA_INSTANCE_ID_3 = "testSourceApplicationInstanceId3"
        const val SA_INSTANCE_ID_4 = "testSourceApplicationInstanceId4"

        const val DESTINATION_INSTANCE_ID_1 = "testDestinationInstanceId1"
        const val DESTINATION_INSTANCE_ID_2 = "testDestinationInstanceId2"

        const val STATUS_EVENT_NAME_1 = "testStatusEventName1"
        const val STATUS_EVENT_NAME_2 = "testStatusEventName2"
        const val STATUS_EVENT_NAME_3 = "testStatusEventName3"
        const val STATUS_EVENT_NAME_4 = "testStatusEventName4"
        const val STATUS_EVENT_NAME_5 = "testStatusEventName5"

        @JvmField
        val ALL_STATUS_EVENT_NAMES =
            listOf(
                STATUS_EVENT_NAME_1,
                STATUS_EVENT_NAME_2,
                STATUS_EVENT_NAME_3,
                STATUS_EVENT_NAME_4,
                STATUS_EVENT_NAME_5,
            )

        const val STORAGE_STATUS_EVENT_NAME_1 = "testStorageStatusEventName1"
        const val STORAGE_STATUS_EVENT_NAME_2 = "testStorageStatusEventName2"

        @JvmField
        val ALL_STORAGE_STATUS_EVENT_NAMES =
            listOf(
                STORAGE_STATUS_EVENT_NAME_1,
                STORAGE_STATUS_EVENT_NAME_2,
            )

        const val UNUSED_LONG_ID = -1L
        const val UNUSED_STRING_ID = "testNotUsedId"

        @JvmField
        val SA1_1_1 =
            EventEntitiesAndExpectedSummary(
                eventEntities =
                    listOf(
                        event(
                            1L,
                            SA_INTEGRATION_ID_1,
                            SA_INSTANCE_ID_1,
                            101L,
                            null,
                            null,
                            STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC),
                        ),
                    ),
                expectedSummary =
                    summary(
                        1L,
                        SA_INTEGRATION_ID_1,
                        SA_INSTANCE_ID_1,
                        101L,
                        null,
                        OffsetDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC),
                        STATUS_EVENT_NAME_1,
                        null,
                        null,
                    ),
            )

        @JvmField
        val SA1_1_2 =
            EventEntitiesAndExpectedSummary(
                eventEntities =
                    listOf(
                        event(
                            1L,
                            SA_INTEGRATION_ID_1,
                            SA_INSTANCE_ID_2,
                            101L,
                            null,
                            null,
                            STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            1L,
                            SA_INTEGRATION_ID_1,
                            SA_INSTANCE_ID_2,
                            101L,
                            null,
                            null,
                            STATUS_EVENT_NAME_2,
                            OffsetDateTime.of(2024, 1, 1, 13, 10, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            1L,
                            SA_INTEGRATION_ID_1,
                            SA_INSTANCE_ID_2,
                            101L,
                            1001L,
                            null,
                            STORAGE_STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 13, 45, 0, 0, ZoneOffset.UTC),
                        ),
                    ),
                expectedSummary =
                    summary(
                        1L,
                        SA_INTEGRATION_ID_1,
                        SA_INSTANCE_ID_2,
                        101L,
                        null,
                        OffsetDateTime.of(2024, 1, 1, 13, 10, 0, 0, ZoneOffset.UTC),
                        STATUS_EVENT_NAME_2,
                        STORAGE_STATUS_EVENT_NAME_1,
                        null,
                    ),
            )

        @JvmField
        val SA2_2_1 =
            EventEntitiesAndExpectedSummary(
                eventEntities =
                    listOf(
                        event(
                            2L,
                            SA_INTEGRATION_ID_2,
                            SA_INSTANCE_ID_1,
                            102L,
                            null,
                            null,
                            STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            2L,
                            SA_INTEGRATION_ID_2,
                            SA_INSTANCE_ID_1,
                            102L,
                            1002L,
                            null,
                            STATUS_EVENT_NAME_2,
                            OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
                        ),
                    ),
                expectedSummary =
                    summary(
                        2L,
                        SA_INTEGRATION_ID_2,
                        SA_INSTANCE_ID_1,
                        102L,
                        1002L,
                        OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
                        STATUS_EVENT_NAME_2,
                        null,
                        null,
                    ),
            )

        @JvmField
        val SA2_3_3 =
            EventEntitiesAndExpectedSummary(
                eventEntities =
                    listOf(
                        event(
                            2L,
                            SA_INTEGRATION_ID_3,
                            SA_INSTANCE_ID_3,
                            103L,
                            null,
                            null,
                            STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            2L,
                            SA_INTEGRATION_ID_3,
                            SA_INSTANCE_ID_3,
                            103L,
                            1003L,
                            null,
                            STATUS_EVENT_NAME_2,
                            OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            2L,
                            SA_INTEGRATION_ID_3,
                            SA_INSTANCE_ID_3,
                            103L,
                            1003L,
                            null,
                            STORAGE_STATUS_EVENT_NAME_2,
                            OffsetDateTime.of(2024, 1, 1, 11, 15, 10, 0, ZoneOffset.UTC),
                        ),
                        event(
                            2L,
                            SA_INTEGRATION_ID_3,
                            SA_INSTANCE_ID_3,
                            103L,
                            1003L,
                            DESTINATION_INSTANCE_ID_1,
                            STATUS_EVENT_NAME_3,
                            OffsetDateTime.of(2024, 1, 1, 11, 15, 20, 0, ZoneOffset.UTC),
                        ),
                    ),
                expectedSummary =
                    summary(
                        2L,
                        SA_INTEGRATION_ID_3,
                        SA_INSTANCE_ID_3,
                        103L,
                        1003L,
                        OffsetDateTime.of(2024, 1, 1, 11, 15, 20, 0, ZoneOffset.UTC),
                        STATUS_EVENT_NAME_3,
                        STORAGE_STATUS_EVENT_NAME_2,
                        DESTINATION_INSTANCE_ID_1,
                    ),
            )

        @JvmField
        val SA3_4_4 =
            EventEntitiesAndExpectedSummary(
                eventEntities =
                    listOf(
                        event(
                            3L,
                            SA_INTEGRATION_ID_4,
                            SA_INSTANCE_ID_4,
                            104L,
                            null,
                            null,
                            STATUS_EVENT_NAME_1,
                            OffsetDateTime.of(2024, 1, 1, 7, 50, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            3L,
                            SA_INTEGRATION_ID_4,
                            SA_INSTANCE_ID_4,
                            104L,
                            null,
                            null,
                            STORAGE_STATUS_EVENT_NAME_2,
                            OffsetDateTime.of(2024, 1, 1, 7, 55, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            3L,
                            SA_INTEGRATION_ID_4,
                            SA_INSTANCE_ID_4,
                            104L,
                            1004L,
                            DESTINATION_INSTANCE_ID_1,
                            STATUS_EVENT_NAME_5,
                            OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 0, ZoneOffset.UTC),
                        ),
                        event(
                            3L,
                            SA_INTEGRATION_ID_4,
                            SA_INSTANCE_ID_4,
                            104L,
                            1004L,
                            DESTINATION_INSTANCE_ID_2,
                            STATUS_EVENT_NAME_5,
                            OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 1000, ZoneOffset.UTC),
                        ),
                        event(
                            3L,
                            SA_INTEGRATION_ID_4,
                            SA_INSTANCE_ID_4,
                            104L,
                            1004L,
                            DESTINATION_INSTANCE_ID_2,
                            STATUS_EVENT_NAME_5,
                            OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC),
                        ),
                    ),
                expectedSummary =
                    summary(
                        3L,
                        SA_INTEGRATION_ID_4,
                        SA_INSTANCE_ID_4,
                        104L,
                        1004L,
                        OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC),
                        STATUS_EVENT_NAME_5,
                        STORAGE_STATUS_EVENT_NAME_2,
                        "$DESTINATION_INSTANCE_ID_2, $DESTINATION_INSTANCE_ID_1",
                    ),
            )

        @JvmField
        val ALL_EVENTS_AND_EXPECTED_SUMMARIES =
            setOf(
                SA1_1_1,
                SA1_1_2,
                SA2_2_1,
                SA2_3_3,
                SA3_4_4,
            )

        private fun event(
            sourceApplicationId: Long,
            sourceApplicationIntegrationId: String,
            sourceApplicationInstanceId: String,
            integrationId: Long,
            instanceId: Long?,
            archiveInstanceId: String?,
            name: String,
            timestamp: OffsetDateTime,
        ): EventEntity {
            return EventEntity
                .builder()
                .instanceFlowHeaders(
                    InstanceFlowHeadersEmbeddable
                        .builder()
                        .sourceApplicationId(sourceApplicationId)
                        .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                        .sourceApplicationInstanceId(sourceApplicationInstanceId)
                        .integrationId(integrationId)
                        .instanceId(instanceId)
                        .archiveInstanceId(archiveInstanceId)
                        .build(),
                ).name(name)
                .timestamp(timestamp)
                .type(EventType.INFO)
                .build()
        }

        private fun summary(
            sourceApplicationId: Long,
            sourceApplicationIntegrationId: String,
            sourceApplicationInstanceId: String,
            integrationId: Long,
            latestInstanceId: Long?,
            latestUpdate: OffsetDateTime,
            latestStatusEventName: String,
            latestStorageStatusEventName: String?,
            destinationInstanceIds: String?,
        ): InstanceFlowSummaryProjection {
            return InstanceFlowSummaryProjection
                .builder()
                .sourceApplicationId(sourceApplicationId)
                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                .integrationId(integrationId)
                .latestInstanceId(latestInstanceId)
                .latestUpdate(latestUpdate)
                .latestStatusEventName(latestStatusEventName)
                .latestStorageStatusEventName(latestStorageStatusEventName)
                .destinationInstanceIds(destinationInstanceIds)
                .build()
        }
    }
}
