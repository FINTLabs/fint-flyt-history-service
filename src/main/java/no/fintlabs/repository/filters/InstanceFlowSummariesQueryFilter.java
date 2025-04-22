package no.fintlabs.repository.filters;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.kafka.support.JavaUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;

@Getter
@Builder
@EqualsAndHashCode
public class InstanceFlowSummariesQueryFilter {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<String> sourceApplicationInstanceIds;
    private final Collection<Long> integrationIds;
    private final Collection<String> statusEventNames;
    @Builder.Default
    private final InstanceStorageStatusQueryFilter instanceStorageStatusQueryFilter = InstanceStorageStatusQueryFilter.EMPTY;
    private final Collection<String> associatedEventNames;
    private final Collection<String> destinationIds;
    private final TimeQueryFilter timeQueryFilter;

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

    public Optional<Collection<String>> getStatusEventNames() {
        return Optional.ofNullable(statusEventNames);
    }

    public Optional<InstanceStorageStatusQueryFilter> getInstanceStorageStatusQueryFilter() {
        return Optional.ofNullable(instanceStorageStatusQueryFilter);
    }

    public Optional<Collection<String>> getAssociatedEventNames() {
        return Optional.ofNullable(associatedEventNames);
    }

    public Optional<Collection<String>> getDestinationIds() {
        return Optional.ofNullable(destinationIds);
    }

    public Optional<TimeQueryFilter> getTimeQueryFilter() {
        return Optional.ofNullable(timeQueryFilter);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationIds, sourceApplicationIds -> joiner.add("sourceApplicationIds=" + sourceApplicationIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationIntegrationIds, sourceApplicationIntegrationIds ->
                        joiner.add("sourceApplicationIntegrationIds=" + sourceApplicationIntegrationIds)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationInstanceIds, sourceApplicationInstanceIds ->
                        joiner.add("sourceApplicationInstanceIds=" + sourceApplicationInstanceIds)
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
                instanceStorageStatusQueryFilter, instanceStorageStatusQueryFilter ->
                        joiner.add("instanceStorageStatusQueryFilter=" + instanceStorageStatusQueryFilter)
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
