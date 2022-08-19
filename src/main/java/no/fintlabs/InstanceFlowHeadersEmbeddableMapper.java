package no.fintlabs;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.model.InstanceFlowHeadersEmbeddable;
import org.springframework.stereotype.Service;

@Service
public class InstanceFlowHeadersEmbeddableMapper {

    public InstanceFlowHeadersEmbeddable getSkjemaEventHeaders(InstanceFlowHeaders instanceFlowHeaders) {
        return InstanceFlowHeadersEmbeddable.builder()
                .orgId(instanceFlowHeaders.getOrgId())
                .service(instanceFlowHeaders.getService())
                .sourceApplication(instanceFlowHeaders.getSourceApplication())
                .sourceApplicationIntegrationId(instanceFlowHeaders.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(instanceFlowHeaders.getSourceApplicationInstanceId())
                .correlationId(instanceFlowHeaders.getCorrelationId())
                .integrationId(instanceFlowHeaders.getIntegrationId())
                .instanceId(instanceFlowHeaders.getInstanceId())
                .configurationId(instanceFlowHeaders.getConfigurationId())
                .caseId(instanceFlowHeaders.getCaseId())
                .dispatchId(instanceFlowHeaders.getDispatchId())
                .archiveCaseFolderId(null) // TODO: 21/06/2022 Add when instanceFlowHeaders support this property
                .build();
    }

}
