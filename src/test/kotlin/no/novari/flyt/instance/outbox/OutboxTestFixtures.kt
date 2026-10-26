package no.novari.flyt.instance.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.instance.InstanceReceivedMessageId
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.Instant
import java.util.Date
import java.util.UUID

internal fun outboxObjectMapper(): ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

internal fun outboxEvent(offset: Long = 0): InstanceRegisteredOutboxEvent {
    val instant = Instant.parse("2026-10-23T10:20:30.123Z")
    val headers =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("source-integration")
            .sourceApplicationInstanceId("source-instance")
            .correlationId(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
            .instanceId(123L)
            .integrationId(42L)
            .configurationId(43L)
            .archiveInstanceId("archive-instance")
            .fileIds(listOf(UUID.fromString("16b1b2c3-5d6e-47f8-90ab-123456789abc")))
            .build()
    val child = InstanceObjectDto(124L, mutableMapOf("child" to "value"), createdAt = Date.from(instant))
    val instance =
        InstanceObjectDto(
            123L,
            mutableMapOf("root" to "value"),
            mutableMapOf("children" to listOf(child), "empty" to emptyList()),
            Date.from(instant),
        )
    return InstanceRegisteredOutboxEvent(
        UUID.randomUUID(),
        InstanceReceivedMessageId("instance-received-test", 0, offset, "test-cluster", UUID.randomUUID()),
        headers,
        instance,
        instant,
        "stored-history-app",
    )
}

internal fun outboxClaim(
    event: InstanceRegisteredOutboxEvent,
    codec: InstanceRegisteredOutboxCodec,
): InstanceRegisteredOutboxClaim =
    InstanceRegisteredOutboxClaim(
        event.id,
        event.messageId,
        codec.encodeHeaders(event.headers),
        codec.encodeInstance(event.instance),
        event.timestamp,
        event.applicationId,
        UUID.randomUUID(),
        1,
    )
