package no.novari.flyt.history.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.novari.flyt.history.EventService
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl

class StatisticsMetricsPublisherTest {
    private lateinit var eventService: EventService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var statisticsMetricsPublisher: StatisticsMetricsPublisher

    @BeforeEach
    fun setup() {
        eventService = mock()
        meterRegistry = SimpleMeterRegistry()
        statisticsMetricsPublisher = StatisticsMetricsPublisher(eventService, meterRegistry)
    }

    @Test
    fun `given no integration statistics when refresh metrics then publish no data integration series`() {
        val totals: InstanceStatisticsProjection = mock()
        whenever(totals.getTotal()).thenReturn(0L)
        whenever(totals.getInProgress()).thenReturn(0L)
        whenever(totals.getTransferred()).thenReturn(0L)
        whenever(totals.getAborted()).thenReturn(0L)
        whenever(totals.getFailed()).thenReturn(0L)
        whenever(eventService.getStatisticsForAllSourceApplications()).thenReturn(totals)

        val emptySlice: Slice<IntegrationStatisticsProjection> =
            SliceImpl(listOf(), PageRequest.of(0, 500), false)
        whenever(eventService.getIntegrationStatistics(any(), any())).thenReturn(emptySlice)

        statisticsMetricsPublisher.refreshMetrics()

        val instanceTotalGauge: Gauge? =
            meterRegistry
                .find("flyt.history.instance.count")
                .tag("status", "total")
                .gauge()
        val integrationNoDataGauge: Gauge? =
            meterRegistry
                .find("flyt.history.integration.count")
                .tag("sourceapplication_id", "__none__")
                .tag("integration_id", "__none__")
                .tag("status", "total")
                .gauge()

        assertThat(instanceTotalGauge).isNotNull()
        assertThat(integrationNoDataGauge).isNotNull()
        assertThat(integrationNoDataGauge!!.value()).isEqualTo(0.0)
    }

    @Test
    fun `when refresh metrics then page integration statistics with stable sort`() {
        val totals: InstanceStatisticsProjection = mock()
        whenever(totals.getTotal()).thenReturn(0L)
        whenever(totals.getInProgress()).thenReturn(0L)
        whenever(totals.getTransferred()).thenReturn(0L)
        whenever(totals.getAborted()).thenReturn(0L)
        whenever(totals.getFailed()).thenReturn(0L)
        whenever(eventService.getStatisticsForAllSourceApplications()).thenReturn(totals)

        val emptySlice: Slice<IntegrationStatisticsProjection> =
            SliceImpl(listOf(), PageRequest.of(0, 500), false)
        whenever(eventService.getIntegrationStatistics(any(), any())).thenReturn(emptySlice)

        statisticsMetricsPublisher.refreshMetrics()

        val pageableCaptor = argumentCaptor<Pageable>()
        verify(eventService, times(1)).getIntegrationStatistics(any(), pageableCaptor.capture())

        assertThat(
            pageableCaptor.firstValue.sort
                .toList()
                .map { it.property },
        ).containsExactly("integrationId", "sourceApplicationId")
    }
}
