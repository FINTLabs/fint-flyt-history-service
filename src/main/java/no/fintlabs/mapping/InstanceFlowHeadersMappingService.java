package no.fintlabs.mapping;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.springframework.stereotype.Service;

@Service
public class InstanceFlowHeadersMappingService {

    public InstanceFlowHeadersEmbeddable toEmbeddable(InstanceFlowHeaders instanceFlowHeaders) {
        return InstanceFlowHeadersEmbeddable.builder()
                .sourceApplicationId(instanceFlowHeaders.getSourceApplicationId())
                .sourceApplicationIntegrationId(instanceFlowHeaders.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(instanceFlowHeaders.getSourceApplicationInstanceId())
                .fileIds(instanceFlowHeaders.getFileIds())
                .correlationId(instanceFlowHeaders.getCorrelationId())
                .integrationId(instanceFlowHeaders.getIntegrationId())
                .instanceId(instanceFlowHeaders.getInstanceId())
                .configurationId(instanceFlowHeaders.getConfigurationId())
                .archiveInstanceId(instanceFlowHeaders.getArchiveInstanceId())
                .build();
    }

    public InstanceFlowHeaders toInstanceFlowHeaders(InstanceFlowHeadersEmbeddable embeddable) {
        return InstanceFlowHeaders.builder()
                .sourceApplicationId(embeddable.getSourceApplicationId())
                .sourceApplicationIntegrationId(embeddable.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(embeddable.getSourceApplicationInstanceId())
                .fileIds(embeddable.getFileIds())
                .correlationId(embeddable.getCorrelationId())
                .integrationId(embeddable.getIntegrationId())
                .instanceId(embeddable.getInstanceId())
                .configurationId(embeddable.getConfigurationId())
                .archiveInstanceId(embeddable.getArchiveInstanceId())
                .build();
    }

}
