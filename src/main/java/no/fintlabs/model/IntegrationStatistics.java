package no.fintlabs.model;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

@Getter
@EqualsAndHashCode
@Jacksonized
@Builder
public class IntegrationStatistics {
    private String sourceApplicationIntegrationId;
    private Long dispatchedInstances;
    private Long currentErrors;
}
