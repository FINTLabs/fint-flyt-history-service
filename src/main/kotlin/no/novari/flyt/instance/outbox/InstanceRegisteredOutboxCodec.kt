package no.novari.flyt.instance.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Component
import java.util.Date

@Component
@ConditionalOnInstanceFunctionality
class InstanceRegisteredOutboxCodec(
    private val objectMapper: ObjectMapper,
) {
    fun encodeHeaders(headers: InstanceFlowHeaders): String = objectMapper.writeValueAsString(headers)

    fun encodeInstance(instance: InstanceObjectDto): String =
        objectMapper.writeValueAsString(InstanceSnapshot.from(instance))

    fun decode(claim: InstanceRegisteredOutboxClaim): InstanceRegisteredOutboxEvent =
        InstanceRegisteredOutboxEvent(
            claim.id,
            claim.messageId,
            objectMapper.readValue(claim.headersJson, InstanceFlowHeaders::class.java),
            objectMapper.readValue(claim.instanceJson, InstanceSnapshot::class.java).toDto(),
            claim.timestamp,
            claim.applicationId,
        )

    private data class InstanceSnapshot(
        val id: Long?,
        val valuePerKey: Map<String, String>,
        val objectCollectionPerKey: Map<String, List<InstanceSnapshot>>,
        val createdAt: Date?,
    ) {
        fun toDto(): InstanceObjectDto =
            InstanceObjectDto(
                id,
                valuePerKey.toMutableMap(),
                objectCollectionPerKey.mapValues { (_, objects) -> objects.map { it.toDto() } }.toMutableMap(),
                createdAt,
            )

        companion object {
            fun from(dto: InstanceObjectDto): InstanceSnapshot =
                InstanceSnapshot(
                    dto.id,
                    dto.valuePerKey,
                    dto.objectCollectionPerKey.mapValues { (_, objects) -> objects.map(::from) },
                    dto.createdAt,
                )
        }
    }
}
