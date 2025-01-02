package no.fintlabs.repository.filters;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

@Builder
public class InstanceInfoQueryFilter {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<String> sourceApplicationInstanceIds;
    private final Collection<Long> integrationIds;
    private final OffsetDateTime latestStatusTimestampMin;
    private final OffsetDateTime latestStatusTimestampMax;
    private final Collection<String> statusEventNames;
    private final InstanceStorageStatusQueryFilter storageStatusFilter;
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

    public Optional<Collection<String>> getStatusEventNames() {
        return Optional.ofNullable(statusEventNames);
    }

    public Optional<InstanceStorageStatusQueryFilter> getStorageStatusFilter() {
        return Optional.ofNullable(storageStatusFilter);
    }

    public Optional<Collection<String>> getAssociatedEventNames() {
        return Optional.ofNullable(associatedEventNames);
    }

    public Optional<Collection<String>> getDestinationIds() {
        return Optional.ofNullable(destinationIds);
    }
}
