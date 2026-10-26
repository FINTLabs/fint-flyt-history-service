package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.InstanceReceivedMessageId
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import java.time.Instant
import java.util.UUID

data class InstanceRegisteredOutboxEvent(
    val id: UUID,
    val messageId: InstanceReceivedMessageId,
    val headers: InstanceFlowHeaders,
    val instance: InstanceObjectDto,
    val timestamp: Instant,
    val applicationId: String,
)

data class InstanceRegisteredOutboxClaim(
    val id: UUID,
    val messageId: InstanceReceivedMessageId,
    val headersJson: String,
    val instanceJson: String,
    val timestamp: Instant,
    val applicationId: String,
    val token: UUID,
    val attempts: Int,
)

data class InstanceRegisteredOutboxStatistics(
    val pending: Long,
    val oldestAgeSeconds: Double,
)
