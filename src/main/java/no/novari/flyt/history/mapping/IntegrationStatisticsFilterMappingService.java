package no.novari.flyt.history.mapping;

import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter;
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter;
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
