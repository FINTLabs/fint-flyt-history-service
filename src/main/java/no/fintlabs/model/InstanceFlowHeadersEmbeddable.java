package no.fintlabs.model;

import lombok.*;

import javax.persistence.Embeddable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Builder(toBuilder = true)
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
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
