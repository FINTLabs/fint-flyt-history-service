package no.fintlabs;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.model.InstanceFlowHeadersEmbeddable;
import org.springframework.stereotype.Service;

@Service
public class InstanceFlowHeadersEmbeddableMapper {

    public InstanceFlowHeadersEmbeddable toEmbeddable(InstanceFlowHeaders instanceFlowHeaders) {
        return InstanceFlowHeadersEmbeddable.builder()
                .orgId(instanceFlowHeaders.getOrgId())
                .sourceApplicationId(instanceFlowHeaders.getSourceApplicationId())
                .sourceApplicationIntegrationId(instanceFlowHeaders.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(instanceFlowHeaders.getSourceApplicationInstanceId())
                .correlationId(instanceFlowHeaders.getCorrelationId())
                .instanceId(instanceFlowHeaders.getInstanceId())
                .configurationId(instanceFlowHeaders.getConfigurationId())
                .archiveCaseId(instanceFlowHeaders.getArchiveCaseId())
                .build();
    }

    public InstanceFlowHeaders toInstanceFlowHeaders(InstanceFlowHeadersEmbeddable embeddable) {
        return InstanceFlowHeaders.builder()
                .orgId(embeddable.getOrgId())
                .sourceApplicationId(embeddable.getSourceApplicationId())
                .sourceApplicationIntegrationId(embeddable.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(embeddable.getSourceApplicationInstanceId())
                .correlationId(embeddable.getCorrelationId())
                .instanceId(embeddable.getInstanceId())
                .configurationId(embeddable.getConfigurationId())
                .archiveCaseId(embeddable.getArchiveCaseId())
                .build();
    }

}
