package no.fintlabs.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@Builder
public class IntegrationStatistics {
    private String sourceApplicationIntegrationId;
    private Long dispatchedInstances;
    private Long currentErrors;
}
