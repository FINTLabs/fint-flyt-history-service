package no.fintlabs.model.statistics;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class InstanceStatistics {
    private final Long numberOfInstances;

    private final Long numberOfTransferredInstances; // TODO 20/12/2024 eivindmorch: Rename to successful?

    private final Long numberOfInProgressInstances;

    // TODO 20/12/2024 eivindmorch: Rejected (or cancelled/aborted) instances

    private final Long numberOfFailedInstances;

}
