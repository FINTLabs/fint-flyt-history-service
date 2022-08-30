package no.fintlabs.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrationStatistics {
    private Long dispatchedInstances;
    private Long currentErrors;
}
