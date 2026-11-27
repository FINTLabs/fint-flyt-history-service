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
        return InstanceFlowHeaders(
            sourceApplicationId = requireNotNull(embeddable.sourceApplicationId),
            // The same exception as the v7 builder for a missing correlationId.
            correlationId =
                embeddable.correlationId ?: throw NullPointerException("correlationId is marked non-null but is null"),
            sourceApplicationIntegrationId = requireNotNull(embeddable.sourceApplicationIntegrationId),
            sourceApplicationInstanceId = requireNotNull(embeddable.sourceApplicationInstanceId),
            fileIds = embeddable.fileIds.toList(),
            integrationId = embeddable.integrationId,
            instanceId = embeddable.instanceId,
            configurationId = embeddable.configurationId,
            archiveInstanceId = embeddable.archiveInstanceId,
        )
    }
}
