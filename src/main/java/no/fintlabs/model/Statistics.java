package no.fintlabs.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Statistics {
    private final Long dispatchedInstances;
    private final Long currentErrors;
}
