package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import java.time.Instant
import java.util.Date
import java.util.UUID

internal fun retryHeaders(instanceId: Long = 123L): InstanceFlowHeaders =
    InstanceFlowHeaders
        .builder()
        .sourceApplicationId(1L)
        .sourceApplicationIntegrationId("source-integration")
        .sourceApplicationInstanceId("source-instance")
        .correlationId(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
        .instanceId(instanceId)
        .integrationId(42L)
        .configurationId(43L)
        .archiveInstanceId("archive-instance")
        .fileIds(listOf(UUID.fromString("16b1b2c3-5d6e-47f8-90ab-123456789abc")))
        .build()

internal fun retryInstance(): InstanceObjectDto {
    val createdAt = Date.from(Instant.parse("2026-10-23T10:20:30.123Z"))
    return InstanceObjectDto(
        id = 123L,
        valuePerKey = mutableMapOf("root" to "value"),
        objectCollectionPerKey =
            mutableMapOf(
                "children" to listOf(InstanceObjectDto(124L, mutableMapOf("child" to "value"), createdAt = createdAt)),
                "empty" to emptyList(),
            ),
        createdAt = createdAt,
    )
}
