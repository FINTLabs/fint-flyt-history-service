package no.fintlabs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@Embeddable
public class InstanceFlowHeadersEmbeddable {
    private String orgId;
    private String service;
    private String sourceApplication;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;
    private String correlationId;
    private String integrationId;
    private String instanceId;
    private String configurationId;
    private String caseId;
    private String dispatchId;
    private String archiveCaseFolderId; // TODO: 21/06/2022 Add to flyt-kafka headers
}
