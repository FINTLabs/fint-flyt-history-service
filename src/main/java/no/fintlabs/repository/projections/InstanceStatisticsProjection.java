package no.fintlabs.repository.projections;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
// TODO 20/12/2024 eivindmorch: Interface?
// TODO 20/12/2024 eivindmorch: Include stored, stored and deleted, and never stored?
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class InstanceStatisticsProjection {
    private final Long numberOfInstances;

    private final Long numberOfInProgressInstances;

    private final Long numberOfTransferredInstances; // TODO 20/12/2024 eivindmorch: Rename to successful?

    private final Long numberOfRejectedInstances; // TODO 20/12/2024 eivindmorch: Rename to aborted?

    private final Long numberOfFailedInstances;

}
