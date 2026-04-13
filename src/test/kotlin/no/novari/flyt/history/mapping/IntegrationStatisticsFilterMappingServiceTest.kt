package no.novari.flyt.history.mapping

import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntegrationStatisticsFilterMappingServiceTest {
    private lateinit var integrationStatisticsFilterMappingService: IntegrationStatisticsFilterMappingService

    @BeforeEach
    fun setup() {
        integrationStatisticsFilterMappingService = IntegrationStatisticsFilterMappingService()
    }

    @Test
    fun `given null integration statistics filter when to query filter then throw exception`() {
        assertThrows<IllegalArgumentException> {
            integrationStatisticsFilterMappingService.toQueryFilter(null)
        }
    }

    @Test
    fun `given empty integration statistics filter when to query filter then return empty filter`() {
        val queryFilter: IntegrationStatisticsQueryFilter =
            integrationStatisticsFilterMappingService.toQueryFilter(
                IntegrationStatisticsFilter.builder().build(),
            )

        assertThat(queryFilter.sourceApplicationIds).isNull()
        assertThat(queryFilter.sourceApplicationIntegrationIds).isNull()
        assertThat(queryFilter.integrationIds).isNull()
    }

    @Test
    fun `given integration statistics filter with values when to query filter then return filter with values`() {
        val queryFilter: IntegrationStatisticsQueryFilter =
            integrationStatisticsFilterMappingService.toQueryFilter(
                IntegrationStatisticsFilter
                    .builder()
                    .sourceApplicationIds(listOf(1L, 2L))
                    .sourceApplicationIntegrationIds(
                        listOf(
                            "testSourceApplicationIntegrationId1",
                            "testSourceApplicationIntegrationId1",
                        ),
                    ).integrationIds(listOf(10L, 11L))
                    .build(),
            )

        assertThat(queryFilter.sourceApplicationIds).isEqualTo(listOf(1L, 2L))
        assertThat(queryFilter.sourceApplicationIntegrationIds)
            .isEqualTo(
                listOf(
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationIntegrationId1",
                ),
            )
        assertThat(queryFilter.integrationIds).isEqualTo(listOf(10L, 11L))
    }
}
