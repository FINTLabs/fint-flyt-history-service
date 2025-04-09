package no.fintlabs.mapping;

import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import org.springframework.stereotype.Service;

@Service
public class IntegrationStatisticsFilterMappingService {

    public IntegrationStatisticsQueryFilter toQueryFilter(IntegrationStatisticsFilter integrationStatisticsFilter) {
        if (integrationStatisticsFilter == null) {
            throw new IllegalArgumentException("IntegrationStatisticsFilter must not be null");
        }
        return IntegrationStatisticsQueryFilter
                .builder()
                .sourceApplicationIds(integrationStatisticsFilter.getSourceApplicationIds())
                .sourceApplicationIntegrationIds(integrationStatisticsFilter.getSourceApplicationIntegrationIds())
                .integrationIds(integrationStatisticsFilter.getIntegrationIds())
                .build();
    }
}
