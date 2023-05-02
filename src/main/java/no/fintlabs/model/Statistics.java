package no.fintlabs.model;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

@Getter
@EqualsAndHashCode
@Jacksonized
@Builder
public class Statistics {
    private final Long dispatchedInstances;
    private final Long currentErrors;
}
