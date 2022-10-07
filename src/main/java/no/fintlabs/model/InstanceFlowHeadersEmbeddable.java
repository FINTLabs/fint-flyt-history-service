package no.fintlabs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@Embeddable
public class InstanceFlowHeadersEmbeddable {
    private Long sourceApplicationId;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;

    private UUID correlationId;
    private Long integrationId;
    private Long instanceId;
    private Long configurationId;

    private String archiveInstanceId;
}
