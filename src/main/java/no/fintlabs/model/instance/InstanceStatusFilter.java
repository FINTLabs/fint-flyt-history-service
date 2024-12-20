package no.fintlabs.model.instance;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

@Jacksonized
@Builder(toBuilder = true)
public class InstanceStatusFilter {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<String> sourceApplicationInstanceIds;
    private final Collection<Long> integrationIds;
    private final OffsetDateTime latestStatusTimestampMin;
    private final OffsetDateTime latestStatusTimestampMax;
    private final Collection<InstanceStatus> statuses;
    private final Collection<InstanceStorageStatus> storageStatuses;
    private final Collection<String> associatedEventNames;
    private final Collection<String> destinationIds;

    public Optional<Collection<Long>> getSourceApplicationIds() {
        return Optional.ofNullable(sourceApplicationIds);
    }

    public Optional<Collection<String>> getSourceApplicationIntegrationIds() {
        return Optional.ofNullable(sourceApplicationIntegrationIds);
    }

    public Optional<Collection<String>> getSourceApplicationInstanceIds() {
        return Optional.ofNullable(sourceApplicationInstanceIds);
    }

    public Optional<Collection<Long>> getIntegrationIds() {
        return Optional.ofNullable(integrationIds);
    }

    public Optional<OffsetDateTime> getLatestStatusTimestampMin() {
        return Optional.ofNullable(latestStatusTimestampMin);
    }

    public Optional<OffsetDateTime> getLatestStatusTimestampMax() {
        return Optional.ofNullable(latestStatusTimestampMax);
    }

    private Optional<Collection<InstanceStatus>> getStatuses() {
        return Optional.ofNullable(statuses);
    }

    // TODO 20/12/2024 eivindmorch: Change to create in constructor
    public Optional<Collection<String>> getStatusEventNames() {
        return getStatuses().map(InstanceStatusEvent::getAllEventNames);
    }

    // TODO 20/12/2024 eivindmorch: Change to create in constructor
    public Optional<InstanceStorageStatusFilter> getStorageStatusFilter() {
        return Optional.ofNullable(storageStatuses)
                .map(statuses ->
                        new InstanceStorageStatusFilter(
                                InstanceStorageStatusEvent.getAllEventNames(statuses),
                                statuses.contains(InstanceStorageStatus.NEVER_STORED)
                        )
                );
    }

    public Optional<Collection<String>> getAssociatedEventNames() {
        return Optional.ofNullable(associatedEventNames);
    }

    public Optional<Collection<String>> getDestinationIds() {
        return Optional.ofNullable(destinationIds);
    }
}
