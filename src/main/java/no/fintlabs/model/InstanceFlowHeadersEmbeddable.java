package no.fintlabs.model;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.persistence.Embeddable;
import java.util.UUID;

@Getter
@EqualsAndHashCode
@Jacksonized
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
