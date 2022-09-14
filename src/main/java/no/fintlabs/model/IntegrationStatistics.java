package no.fintlabs.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrationStatistics {
    private String sourceApplicationIntegrationId;
    private Long dispatchedInstances;
    private Long currentErrors;
}
