package no.novari.flyt.history.metrics;

import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import no.novari.flyt.history.EventService;
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter;
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection;
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsMetricsPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsMetricsPublisher.class);

    private static final String INSTANCE_METRIC = "flyt.history.instance.count";
    private static final String INTEGRATION_METRIC = "flyt.history.integration.count";
    private static final String TAG_STATUS = "status";
    private static final String TAG_INTEGRATION_ID = "integration_id";
    private static final String STATUS_TOTAL = "total";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_TRANSFERRED = "transferred";
    private static final String STATUS_ABORTED = "aborted";
    private static final String STATUS_FAILED = "failed";
    private static final String INTEGRATION_ID_NO_DATA = "__none__";

    private static final int DEFAULT_PAGE_SIZE = 500;

    private final EventService eventService;
    private final MultiGauge instanceGauge;
    private final MultiGauge integrationGauge;

    public StatisticsMetricsPublisher(EventService eventService, MeterRegistry meterRegistry) {
        this.eventService = eventService;
        this.instanceGauge = MultiGauge
                .builder(INSTANCE_METRIC)
                .description("Current count of instance-flow statuses (latest events).")
                .register(meterRegistry);
        this.integrationGauge = MultiGauge
                .builder(INTEGRATION_METRIC)
                .description("Current count of instance-flow statuses per integration (latest events).")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${novari.flyt.history-service.metrics.refresh-ms:60000}")
    public void refreshMetrics() {
        try {
            publishInstanceTotals();
            publishIntegrationTotals();
        } catch (Exception e) {
            logger.warn("Failed to refresh statistics metrics", e);
        }
    }

    private void publishInstanceTotals() {
        InstanceStatisticsProjection totals = eventService.getStatistics(List.of());

        List<MultiGauge.Row<?>> rows = List.of(
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_TOTAL), safeValue(totals.getTotal())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_IN_PROGRESS), safeValue(totals.getInProgress())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_TRANSFERRED), safeValue(totals.getTransferred())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_ABORTED), safeValue(totals.getAborted())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_FAILED), safeValue(totals.getFailed()))
        );

        instanceGauge.register(rows, true);
    }

    private void publishIntegrationTotals() {
        List<IntegrationStatisticsProjection> allIntegrations = new ArrayList<>();
        IntegrationStatisticsFilter filter = IntegrationStatisticsFilter.builder().build();

        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by("integrationId"));
        while (true) {
            Slice<IntegrationStatisticsProjection> slice = eventService.getIntegrationStatistics(filter, pageable);
            allIntegrations.addAll(slice.getContent());
            if (!slice.hasNext()) {
                break;
            }
            pageable = slice.nextPageable();
        }

        List<MultiGauge.Row<?>> rows = new ArrayList<>(allIntegrations.size() * 5);
        for (IntegrationStatisticsProjection integration : allIntegrations) {
            String integrationId = integration.getIntegrationId() == null
                    ? "unknown"
                    : integration.getIntegrationId().toString();
            Tags baseTags = Tags.of(TAG_INTEGRATION_ID, integrationId);

            rows.add(MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TOTAL), safeValue(integration.getTotal())));
            rows.add(MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS), safeValue(integration.getInProgress())));
            rows.add(MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TRANSFERRED), safeValue(integration.getTransferred())));
            rows.add(MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_ABORTED), safeValue(integration.getAborted())));
            rows.add(MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_FAILED), safeValue(integration.getFailed())));
        }
        if (rows.isEmpty()) {
            Tags baseTags = Tags.of(TAG_INTEGRATION_ID, INTEGRATION_ID_NO_DATA);
            rows = List.of(
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TOTAL), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TRANSFERRED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_ABORTED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_FAILED), 0)
            );
        }

        integrationGauge.register(rows, true);
    }

    private static Number safeValue(Long value) {
        return value == null ? 0 : value;
    }
}
