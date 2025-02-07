package no.fintlabs.model.statistics;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.SourceApplicationIdFilter;
import no.fintlabs.model.SourceApplicationIdFilterBuilder;

import java.util.Collection;

@Getter
@Jacksonized
@Builder(toBuilder = true)
public class IntegrationStatisticsFilter implements SourceApplicationIdFilter<IntegrationStatisticsFilter> {
    private final Collection<Long> sourceApplicationIds;
    private final Collection<String> sourceApplicationIntegrationIds;
    private final Collection<Long> integrationIds;

    public static class IntegrationStatisticsFilterBuilder
            implements SourceApplicationIdFilterBuilder<IntegrationStatisticsFilter> {

        private Collection<Long> sourceApplicationIds;

        @Override
        public SourceApplicationIdFilterBuilder<IntegrationStatisticsFilter> sourceApplicationId(
                Collection<Long> sourceApplicationIds
        ) {
            this.sourceApplicationIds = sourceApplicationIds;
            return this;
        }
    }

}
