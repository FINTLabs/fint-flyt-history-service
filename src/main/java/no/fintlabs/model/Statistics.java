package no.fintlabs.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@Builder
public class Statistics {
    private final Long dispatchedInstances;
    private final Long currentErrors;
}
