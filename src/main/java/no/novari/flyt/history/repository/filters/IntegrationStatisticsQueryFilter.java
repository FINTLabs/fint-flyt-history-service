package no.novari.flyt.history.repository.filters;

import lombok.Builder;
import org.springframework.kafka.support.JavaUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;

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
                integrationIds, integrationIds -> joiner.add("integrationIds=" + integrationIds)
        );
        return joiner.toString();
    }

}
