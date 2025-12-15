package no.novari.flyt.history.mapping;

import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter;
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationStatisticsFilterMappingServiceTest {

    IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService;

    @BeforeEach
    public void setup() {
        integrationStatisticsFilterMappingService = new IntegrationStatisticsFilterMappingService();
    }

    @Test
    public void givenNullIntegrationStatisticsFilter_whenToQueryFilter_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> integrationStatisticsFilterMappingService.toQueryFilter(null)
        );
    }

    @Test
    public void givenEmptyIntegrationStatisticsFilter_whenToQueryFilter_thenReturnEmptyIntegrationStatisticsFilter() {
        IntegrationStatisticsQueryFilter queryFilter = integrationStatisticsFilterMappingService.toQueryFilter(
                IntegrationStatisticsFilter.builder().build()
        );
        assertThat(queryFilter.getSourceApplicationIds()).isEmpty();
        assertThat(queryFilter.getSourceApplicationIntegrationIds()).isEmpty();
        assertThat(queryFilter.getIntegrationIds()).isEmpty();
    }

    @Test
    public void givenIntegrationStatisticsFilterWithValues_whenToQueryFilter_thenReturnIntegrationStatisticsFilterWithValues() {
        IntegrationStatisticsQueryFilter queryFilter = integrationStatisticsFilterMappingService.toQueryFilter(
                IntegrationStatisticsFilter
                        .builder()
                        .sourceApplicationIds(List.of(1L, 2L))
                        .sourceApplicationIntegrationIds(List.of(
                                "testSourceApplicationIntegrationId1",
                                "testSourceApplicationIntegrationId1"
                        ))
                        .integrationIds(List.of(10L, 11L))
                        .build()
        );
        assertThat(queryFilter.getSourceApplicationIds()).contains(List.of(1L, 2L));
        assertThat(queryFilter.getSourceApplicationIntegrationIds()).contains(
                List.of(
                        "testSourceApplicationIntegrationId1",
                        "testSourceApplicationIntegrationId1"
                )
        );
        assertThat(queryFilter.getIntegrationIds()).contains(List.of(10L, 11L));
    }

}