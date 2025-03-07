package no.fintlabs.repository.filters;

import lombok.Builder;
import org.springframework.kafka.support.JavaUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;

@Builder
public class InstanceFlowSummariesQueryFilter {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<String> sourceApplicationInstanceIds;
    private final Collection<Long> integrationIds;
    private final TimeQueryFilter timeQueryFilter;
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

    public TimeQueryFilter getTimeQueryFilter() {
        return Optional.ofNullable(timeQueryFilter).orElse(TimeQueryFilter.EMPTY);
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

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationIds, sourceApplicationIds -> joiner.add("sourceApplicationIds=" + sourceApplicationIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationIntegrationIds, sourceApplicationIntegrationIds -> joiner.add("sourceApplicationIntegrationIds=" + sourceApplicationIntegrationIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationInstanceIds, sourceApplicationInstanceIds -> joiner.add("sourceApplicationInstanceIds=" + sourceApplicationInstanceIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                integrationIds, integrationIds -> joiner.add("integrationIds=" + integrationIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                timeQueryFilter, timeQueryFilter -> joiner.add("timeQueryFilter=" + timeQueryFilter)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                statusEventNames, statusEventNames -> joiner.add("statusEventNames=" + statusEventNames)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                storageStatusFilter, storageStatusFilter -> joiner.add("storageStatusFilter=" + storageStatusFilter)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                associatedEventNames, associatedEventNames -> joiner.add("associatedEventNames=" + associatedEventNames)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                destinationIds, destinationIds -> joiner.add("destinationIds=" + destinationIds)
        );
        return joiner.toString();
    }

}
