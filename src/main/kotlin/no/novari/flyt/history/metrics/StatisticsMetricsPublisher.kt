package no.novari.flyt.history.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.novari.flyt.history.EventService
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class StatisticsMetricsPublisher(
    private val eventService: EventService,
    meterRegistry: MeterRegistry,
    private val environment: Environment,
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

    private val sourceApplicationGauge =
        MultiGauge
            .builder(SOURCE_APPLICATION_METRIC)
            .description("Current count of instance-flow statuses per source application (latest events).")
            .register(meterRegistry)

    @Scheduled(fixedDelayString = "\${novari.flyt.history-service.metrics.refresh-ms:60000}")
    fun refreshMetrics() {
        try {
            publishInstanceTotals()
            val allIntegrations = fetchIntegrationStatistics()
            publishIntegrationTotals(allIntegrations)
            publishSourceApplicationTotals(allIntegrations)
        } catch (exception: Exception) {
            logger.warn("Failed to refresh statistics metrics", exception)
        }
    }

    private fun publishInstanceTotals() {
        val totals = eventService.getStatisticsForAllSourceApplications()
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

    private fun fetchIntegrationStatistics(): List<IntegrationStatisticsProjection> {
        val allIntegrations = mutableListOf<IntegrationStatisticsProjection>()
        val filter = IntegrationStatisticsFilter.builder().build()

        var pageable: Pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by("integrationId", "sourceApplicationId"))
        while (true) {
            val slice = eventService.getIntegrationStatistics(filter, pageable)
            allIntegrations += slice.content
            if (!slice.hasNext()) {
                break
            }
            pageable = slice.nextPageable()
        }

        return allIntegrations
    }

    private fun publishIntegrationTotals(allIntegrations: List<IntegrationStatisticsProjection>) {
        var rows =
            allIntegrations.flatMap { integration ->
                val integrationId = integration.getIntegrationId()?.toString() ?: "unknown"
                val sourceApplicationId = integration.getSourceApplicationId()?.toString() ?: "unknown"
                val baseTags =
                    Tags.of(
                        TAG_SOURCE_APPLICATION_ID,
                        sourceApplicationId,
                        TAG_INTEGRATION_ID,
                        integrationId,
                    )
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
            val baseTags =
                Tags.of(
                    TAG_SOURCE_APPLICATION_ID,
                    SOURCE_APPLICATION_ID_NO_DATA,
                    TAG_INTEGRATION_ID,
                    INTEGRATION_ID_NO_DATA,
                )
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

    private fun publishSourceApplicationTotals(allIntegrations: List<IntegrationStatisticsProjection>) {
        val orgId = resolveOrgId()

        var rows =
            allIntegrations
                .groupBy(IntegrationStatisticsProjection::getSourceApplicationId)
                .entries
                .sortedBy { it.key ?: Long.MAX_VALUE }
                .flatMap { (sourceApplicationId, integrations) ->
                    val sourceApplicationIdTag = sourceApplicationId?.toString() ?: UNKNOWN_SOURCE_APPLICATION_ID
                    val baseTags =
                        Tags.of(
                            TAG_ORG_ID,
                            orgId,
                            TAG_SOURCE_APPLICATION_ID,
                            sourceApplicationIdTag,
                        )
                    listOf(
                        MultiGauge.Row.of(
                            baseTags.and(TAG_STATUS, STATUS_TOTAL),
                            sumByOrZero(integrations) { it.getTotal() },
                        ),
                        MultiGauge.Row.of(
                            baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS),
                            sumByOrZero(integrations) { it.getInProgress() },
                        ),
                        MultiGauge.Row.of(
                            baseTags.and(TAG_STATUS, STATUS_TRANSFERRED),
                            sumByOrZero(integrations) { it.getTransferred() },
                        ),
                        MultiGauge.Row.of(
                            baseTags.and(TAG_STATUS, STATUS_ABORTED),
                            sumByOrZero(integrations) { it.getAborted() },
                        ),
                        MultiGauge.Row.of(
                            baseTags.and(TAG_STATUS, STATUS_FAILED),
                            sumByOrZero(integrations) { it.getFailed() },
                        ),
                    )
                }

        if (rows.isEmpty()) {
            val baseTags =
                Tags.of(
                    TAG_ORG_ID,
                    orgId,
                    TAG_SOURCE_APPLICATION_ID,
                    SOURCE_APPLICATION_ID_NO_DATA,
                )
            rows =
                listOf(
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TOTAL), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_IN_PROGRESS), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_TRANSFERRED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_ABORTED), 0),
                    MultiGauge.Row.of(baseTags.and(TAG_STATUS, STATUS_FAILED), 0),
                )
        }

        sourceApplicationGauge.register(rows, true)
    }

    private fun safeValue(value: Long?): Number = value ?: 0

    private fun sumByOrZero(
        integrations: List<IntegrationStatisticsProjection>,
        valueProvider: (IntegrationStatisticsProjection) -> Long?,
    ): Number = integrations.sumOf { valueProvider(it) ?: 0L }

    private fun resolveOrgId(): String {
        return environment.getProperty("fint.org-id")
            ?: environment.getProperty("novari.kafka.topic.org-id")
            ?: environment.getProperty("novari.kafka.topic.orgId")
            ?: "unknown"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StatisticsMetricsPublisher::class.java)

        private const val INSTANCE_METRIC = "flyt.history.instance.count"
        private const val INTEGRATION_METRIC = "flyt.history.integration.count"
        private const val SOURCE_APPLICATION_METRIC = "flyt.history.source.application.count"
        private const val TAG_STATUS = "status"
        private const val TAG_SOURCE_APPLICATION_ID = "sourceapplication_id"
        private const val TAG_INTEGRATION_ID = "integration_id"
        private const val TAG_ORG_ID = "org_id"
        private const val STATUS_TOTAL = "total"
        private const val STATUS_IN_PROGRESS = "in_progress"
        private const val STATUS_TRANSFERRED = "transferred"
        private const val STATUS_ABORTED = "aborted"
        private const val STATUS_FAILED = "failed"
        private const val SOURCE_APPLICATION_ID_NO_DATA = "__none__"
        private const val UNKNOWN_SOURCE_APPLICATION_ID = "unknown"
        private const val INTEGRATION_ID_NO_DATA = "__none__"
        private const val DEFAULT_PAGE_SIZE = 500
    }
}
