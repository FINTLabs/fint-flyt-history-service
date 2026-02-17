package no.novari.flyt.history.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import no.novari.flyt.history.EventService;
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection;
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsMetricsPublisherTest {

    private EventService eventService;
    private SimpleMeterRegistry meterRegistry;
    private StatisticsMetricsPublisher statisticsMetricsPublisher;

    @BeforeEach
    void setup() {
        eventService = mock(EventService.class);
        meterRegistry = new SimpleMeterRegistry();
        statisticsMetricsPublisher = new StatisticsMetricsPublisher(eventService, meterRegistry);
    }

    @Test
    void givenNoIntegrationStatistics_whenRefreshMetrics_thenPublishNoDataIntegrationSeries() {
        InstanceStatisticsProjection totals = mock(InstanceStatisticsProjection.class);
        when(totals.getTotal()).thenReturn(0L);
        when(totals.getInProgress()).thenReturn(0L);
        when(totals.getTransferred()).thenReturn(0L);
        when(totals.getAborted()).thenReturn(0L);
        when(totals.getFailed()).thenReturn(0L);
        when(eventService.getStatistics(List.of())).thenReturn(totals);

        Slice<IntegrationStatisticsProjection> emptySlice = new SliceImpl<>(List.of(), PageRequest.of(0, 500), false);
        when(eventService.getIntegrationStatistics(any(), any())).thenReturn(emptySlice);

        statisticsMetricsPublisher.refreshMetrics();

        Gauge instanceTotalGauge = meterRegistry.find("flyt.history.instance.count")
                .tag("status", "total")
                .gauge();
        Gauge integrationNoDataGauge = meterRegistry.find("flyt.history.integration.count")
                .tag("integration_id", "__none__")
                .tag("status", "total")
                .gauge();

        assertThat(instanceTotalGauge).isNotNull();
        assertThat(integrationNoDataGauge).isNotNull();
        assertThat(integrationNoDataGauge.value()).isEqualTo(0.0);
    }
}
