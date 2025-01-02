package no.fintlabs.model.instance;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.SourceApplicationIdFilter;
import no.fintlabs.model.SourceApplicationIdFilterBuilder;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

@Getter
@Jacksonized
@Builder(toBuilder = true)
public class InstanceInfoFilter implements SourceApplicationIdFilter<InstanceInfoFilter> {
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

    public Optional<Collection<InstanceStatus>> getStatuses() {
        return Optional.ofNullable(statuses);
    }

    public Optional<Collection<InstanceStorageStatus>> getStorageStatuses() {
        return Optional.ofNullable(storageStatuses);
    }

    public Optional<Collection<String>> getAssociatedEventNames() {
        return Optional.ofNullable(associatedEventNames);
    }

    public Optional<Collection<String>> getDestinationIds() {
        return Optional.ofNullable(destinationIds);
    }

    public static class InstanceInfoFilterBuilder implements SourceApplicationIdFilterBuilder<InstanceInfoFilter> {

        private Collection<Long> sourceApplicationIds;

        @Override
        public SourceApplicationIdFilterBuilder<InstanceInfoFilter> sourceApplicationId(
                Collection<Long> sourceApplicationIds
        ) {
            this.sourceApplicationIds = sourceApplicationIds;
            return this;
        }
    }

}
