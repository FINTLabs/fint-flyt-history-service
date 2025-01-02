package no.fintlabs.repository.filters;

import lombok.Builder;

import java.util.Collection;
import java.util.Optional;

@Builder
public class IntegrationStatisticsQueryFilter {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<Long> integrationIds;

    public Optional<Collection<Long>> getSourceApplicationIds() {
        return Optional.ofNullable(sourceApplicationIds);
    }

    public Optional<Collection<String>> getSourceApplicationIntegrationIds() {
        return Optional.ofNullable(sourceApplicationIntegrationIds);
    }

    public Optional<Collection<Long>> getIntegrationIds() {
        return Optional.ofNullable(integrationIds);
    }

}
