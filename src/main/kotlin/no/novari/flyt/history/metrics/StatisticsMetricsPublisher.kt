package no.novari.flyt.history.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.novari.flyt.history.EventService
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class StatisticsMetricsPublisher(
    private val eventService: EventService,
    meterRegistry: MeterRegistry,
) {
    private val instanceGauge =
        MultiGauge
            .builder(INSTANCE_METRIC)
            .description("Current count of instance-flow statuses (latest events).")
            .register(meterRegistry)

    private val integrationGauge =
        MultiGauge
            .builder(INTEGRATION_METRIC)
            .description("Current count of instance-flow statuses per integration (latest events).")
            .register(meterRegistry)

    @Scheduled(fixedDelayString = "\${novari.flyt.history-service.metrics.refresh-ms:60000}")
    fun refreshMetrics() {
        try {
            publishInstanceTotals()
            publishIntegrationTotals()
        } catch (exception: Exception) {
            logger.warn("Failed to refresh statistics metrics", exception)
        }
    }

    private fun publishInstanceTotals() {
        val totals = eventService.getStatistics(emptyList())
        val rows =
            listOf(
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_TOTAL), safeValue(totals.getTotal())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_IN_PROGRESS), safeValue(totals.getInProgress())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_TRANSFERRED), safeValue(totals.getTransferred())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_ABORTED), safeValue(totals.getAborted())),
                MultiGauge.Row.of(Tags.of(TAG_STATUS, STATUS_FAILED), safeValue(totals.getFailed())),
            )

        instanceGauge.register(rows, true)
    }

    private fun publishIntegrationTotals() {
        val allIntegrations = mutableListOf<IntegrationStatisticsProjection>()
        val filter = IntegrationStatisticsFilter.builder().build()

        var pageable: Pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by("integrationId"))
        while (true) {
            val slice = eventService.getIntegrationStatistics(filter, pageable)
            allIntegrations += slice.content
            if (!slice.hasNext()) {
                break
            }
            pageable = slice.nextPageable()
        }

        var rows =
            allIntegrations.flatMap { integration ->
                val integrationId = integration.getIntegrationId()?.toString() ?: "unknown"
                val baseTags = Tags.of(TAG_INTEGRATION_ID, integrationId)
                listOf(
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TOTAL), safeValue(integration.getTotal())),
                    MultiGauge.Row.of(
                        baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS),
                        safeValue(integration.getInProgress()),
                    ),
                    MultiGauge.Row.of(
                        baseTags.and(TAG_STATUS, STATUS_TRANSFERRED),
                        safeValue(integration.getTransferred()),
                    ),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_ABORTED), safeValue(integration.getAborted())),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_FAILED), safeValue(integration.getFailed())),
                )
            }

        if (rows.isEmpty()) {
            val baseTags = Tags.of(TAG_INTEGRATION_ID, INTEGRATION_ID_NO_DATA)
            rows =
                listOf(
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TOTAL), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TRANSFERRED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_ABORTED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_FAILED), 0),
                )
        }

        integrationGauge.register(rows, true)
    }

    private fun safeValue(value: Long?): Number = value ?: 0

    companion object {
        private val logger = LoggerFactory.getLogger(StatisticsMetricsPublisher::class.java)

        private const val INSTANCE_METRIC = "flyt.history.instance.count"
        private const val INTEGRATION_METRIC = "flyt.history.integration.count"
        private const val TAG_STATUS = "status"
        private const val TAG_INTEGRATION_ID = "integration_id"
        private const val STATUS_TOTAL = "total"
        private const val STATUS_IN_PROGRESS = "in_progress"
        private const val STATUS_TRANSFERRED = "transferred"
        private const val STATUS_ABORTED = "aborted"
        private const val STATUS_FAILED = "failed"
        private const val INTEGRATION_ID_NO_DATA = "__none__"
        private const val DEFAULT_PAGE_SIZE = 500
    }
}
