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
    private String sourceApplicationId;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;
    private String correlationId;
    private String instanceId;
    private String configurationId;
    private String archiveCaseId;
}
