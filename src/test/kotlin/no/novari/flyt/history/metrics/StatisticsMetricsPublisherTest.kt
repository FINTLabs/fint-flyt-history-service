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
import org.springframework.mock.env.MockEnvironment

class StatisticsMetricsPublisherTest {
    private lateinit var eventService: EventService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var statisticsMetricsPublisher: StatisticsMetricsPublisher

    @BeforeEach
    fun setup() {
        eventService = mock()
        meterRegistry = SimpleMeterRegistry()
        statisticsMetricsPublisher =
            StatisticsMetricsPublisher(
                eventService,
                meterRegistry,
                MockEnvironment().withProperty("fint.org-id", "fintlabs.no"),
            )
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
        val sourceApplicationNoDataGauge: Gauge? =
            meterRegistry
                .find("flyt.history.source.application.count")
                .tag("org_id", "fintlabs.no")
                .tag("sourceapplication_id", "__none__")
                .tag("status", "total")
                .gauge()

        assertThat(instanceTotalGauge).isNotNull()
        assertThat(integrationNoDataGauge).isNotNull()
        assertThat(integrationNoDataGauge!!.value()).isEqualTo(0.0)
        assertThat(sourceApplicationNoDataGauge).isNotNull()
        assertThat(sourceApplicationNoDataGauge!!.value()).isEqualTo(0.0)
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

    @Test
    fun `when refresh metrics then publish aggregated source application totals`() {
        val totals: InstanceStatisticsProjection = mock()
        whenever(totals.getTotal()).thenReturn(7L)
        whenever(totals.getInProgress()).thenReturn(0L)
        whenever(totals.getTransferred()).thenReturn(6L)
        whenever(totals.getAborted()).thenReturn(1L)
        whenever(totals.getFailed()).thenReturn(0L)
        whenever(eventService.getStatisticsForAllSourceApplications()).thenReturn(totals)

        val firstIntegration: IntegrationStatisticsProjection = mock()
        whenever(firstIntegration.getSourceApplicationId()).thenReturn(1L)
        whenever(firstIntegration.getIntegrationId()).thenReturn(101L)
        whenever(firstIntegration.getTotal()).thenReturn(3L)
        whenever(firstIntegration.getInProgress()).thenReturn(0L)
        whenever(firstIntegration.getTransferred()).thenReturn(2L)
        whenever(firstIntegration.getAborted()).thenReturn(1L)
        whenever(firstIntegration.getFailed()).thenReturn(0L)

        val secondIntegration: IntegrationStatisticsProjection = mock()
        whenever(secondIntegration.getSourceApplicationId()).thenReturn(1L)
        whenever(secondIntegration.getIntegrationId()).thenReturn(102L)
        whenever(secondIntegration.getTotal()).thenReturn(2L)
        whenever(secondIntegration.getInProgress()).thenReturn(0L)
        whenever(secondIntegration.getTransferred()).thenReturn(2L)
        whenever(secondIntegration.getAborted()).thenReturn(0L)
        whenever(secondIntegration.getFailed()).thenReturn(0L)

        val thirdIntegration: IntegrationStatisticsProjection = mock()
        whenever(thirdIntegration.getSourceApplicationId()).thenReturn(4L)
        whenever(thirdIntegration.getIntegrationId()).thenReturn(201L)
        whenever(thirdIntegration.getTotal()).thenReturn(2L)
        whenever(thirdIntegration.getInProgress()).thenReturn(0L)
        whenever(thirdIntegration.getTransferred()).thenReturn(2L)
        whenever(thirdIntegration.getAborted()).thenReturn(0L)
        whenever(thirdIntegration.getFailed()).thenReturn(0L)

        val slice: Slice<IntegrationStatisticsProjection> =
            SliceImpl(listOf(firstIntegration, secondIntegration, thirdIntegration), PageRequest.of(0, 500), false)
        whenever(eventService.getIntegrationStatistics(any(), any())).thenReturn(slice)

        statisticsMetricsPublisher.refreshMetrics()

        val acosTransferredGauge =
            meterRegistry
                .find("flyt.history.source.application.count")
                .tag("org_id", "fintlabs.no")
                .tag("sourceapplication_id", "1")
                .tag("status", "transferred")
                .gauge()
        val acosAbortedGauge =
            meterRegistry
                .find("flyt.history.source.application.count")
                .tag("org_id", "fintlabs.no")
                .tag("sourceapplication_id", "1")
                .tag("status", "aborted")
                .gauge()
        val vigoTransferredGauge =
            meterRegistry
                .find("flyt.history.source.application.count")
                .tag("org_id", "fintlabs.no")
                .tag("sourceapplication_id", "4")
                .tag("status", "transferred")
                .gauge()

        assertThat(acosTransferredGauge).isNotNull()
        assertThat(acosTransferredGauge!!.value()).isEqualTo(4.0)
        assertThat(acosAbortedGauge).isNotNull()
        assertThat(acosAbortedGauge!!.value()).isEqualTo(1.0)
        assertThat(vigoTransferredGauge).isNotNull()
        assertThat(vigoTransferredGauge!!.value()).isEqualTo(2.0)
    }
}
