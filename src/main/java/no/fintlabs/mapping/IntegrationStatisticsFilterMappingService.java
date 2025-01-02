package no.fintlabs.mapping;

import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import org.springframework.stereotype.Service;

@Service
public class IntegrationStatisticsFilterMappingService {

    public IntegrationStatisticsQueryFilter toQueryFilter(IntegrationStatisticsFilter integrationStatisticsFilter) {
        return IntegrationStatisticsQueryFilter
                .builder()
                .sourceApplicationIds(integrationStatisticsFilter.getSourceApplicationIds().orElse(null))
                .sourceApplicationIntegrationIds(integrationStatisticsFilter.getSourceApplicationIntegrationIds().orElse(null))
                .integrationIds(integrationStatisticsFilter.getIntegrationIds().orElse(null))
                .build();
    }
}
