package no.novari.flyt.history.mapping

import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Service

@Service
class InstanceFlowHeadersMappingService {
    fun toEmbeddable(instanceFlowHeaders: InstanceFlowHeaders): InstanceFlowHeadersEmbeddable {
        return InstanceFlowHeadersEmbeddable(
            sourceApplicationId = instanceFlowHeaders.sourceApplicationId,
            sourceApplicationIntegrationId = instanceFlowHeaders.sourceApplicationIntegrationId,
            sourceApplicationInstanceId = instanceFlowHeaders.sourceApplicationInstanceId,
            fileIds = instanceFlowHeaders.fileIds?.toMutableList() ?: mutableListOf(),
            correlationId = instanceFlowHeaders.correlationId,
            integrationId = instanceFlowHeaders.integrationId,
            instanceId = instanceFlowHeaders.instanceId,
            configurationId = instanceFlowHeaders.configurationId,
            archiveInstanceId = instanceFlowHeaders.archiveInstanceId,
        )
    }

    fun toInstanceFlowHeaders(embeddable: InstanceFlowHeadersEmbeddable): InstanceFlowHeaders {
        val builder =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(requireNotNull(embeddable.sourceApplicationId))
                .sourceApplicationIntegrationId(requireNotNull(embeddable.sourceApplicationIntegrationId))
                .sourceApplicationInstanceId(requireNotNull(embeddable.sourceApplicationInstanceId))
                .fileIds(embeddable.fileIds)
                .integrationId(embeddable.integrationId)
                .instanceId(embeddable.instanceId)
                .configurationId(embeddable.configurationId)
                .archiveInstanceId(embeddable.archiveInstanceId)

        embeddable.correlationId?.let(builder::correlationId)

        return builder.build()
    }
}
