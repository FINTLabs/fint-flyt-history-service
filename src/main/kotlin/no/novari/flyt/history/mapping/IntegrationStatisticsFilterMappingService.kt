package no.novari.flyt.history.mapping

import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import org.springframework.stereotype.Service

@Service
class IntegrationStatisticsFilterMappingService {
    fun toQueryFilter(integrationStatisticsFilter: IntegrationStatisticsFilter?): IntegrationStatisticsQueryFilter {
        requireNotNull(integrationStatisticsFilter) { "IntegrationStatisticsFilter must not be null" }

        return IntegrationStatisticsQueryFilter
            .builder()
            .sourceApplicationIds(integrationStatisticsFilter.sourceApplicationIds)
            .sourceApplicationIntegrationIds(integrationStatisticsFilter.sourceApplicationIntegrationIds)
            .integrationIds(integrationStatisticsFilter.integrationIds)
            .build()
    }
}
