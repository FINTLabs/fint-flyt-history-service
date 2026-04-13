package no.novari.flyt.history.repository.utils.performance

import java.time.OffsetDateTime

class EventGenerationConfig(
    val sourceApplicationId: Long,
    val sourceApplicationIntegrationId: String,
    val integrationId: Long,
    val minTimestamp: OffsetDateTime,
    val maxTimestamp: OffsetDateTime,
    val eventSequenceGenerationConfigs: List<EventSequenceGenerationConfig>,
) {
    override fun toString(): String {
        return "EventGenerationConfig(sourceApplicationId=$sourceApplicationId, " +
            "sourceApplicationIntegrationId=$sourceApplicationIntegrationId, integrationId=$integrationId, " +
            "minTimestamp=$minTimestamp, maxTimestamp=$maxTimestamp, " +
            "eventSequenceGenerationConfigs=$eventSequenceGenerationConfigs)"
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationId: Long? = null
        private var sourceApplicationIntegrationId: String? = null
        private var integrationId: Long? = null
        private var minTimestamp: OffsetDateTime? = null
        private var maxTimestamp: OffsetDateTime? = null
        private var eventSequenceGenerationConfigs: List<EventSequenceGenerationConfig> = emptyList()

        fun sourceApplicationId(sourceApplicationId: Long) = apply { this.sourceApplicationId = sourceApplicationId }

        fun sourceApplicationIntegrationId(sourceApplicationIntegrationId: String) =
            apply { this.sourceApplicationIntegrationId = sourceApplicationIntegrationId }

        fun integrationId(integrationId: Long) = apply { this.integrationId = integrationId }

        fun minTimestamp(minTimestamp: OffsetDateTime) = apply { this.minTimestamp = minTimestamp }

        fun maxTimestamp(maxTimestamp: OffsetDateTime) = apply { this.maxTimestamp = maxTimestamp }

        fun eventSequenceGenerationConfigs(eventSequenceGenerationConfigs: List<EventSequenceGenerationConfig>) =
            apply { this.eventSequenceGenerationConfigs = eventSequenceGenerationConfigs }

        fun build() =
            EventGenerationConfig(
                sourceApplicationId = requireNotNull(sourceApplicationId),
                sourceApplicationIntegrationId = requireNotNull(sourceApplicationIntegrationId),
                integrationId = requireNotNull(integrationId),
                minTimestamp = requireNotNull(minTimestamp),
                maxTimestamp = requireNotNull(maxTimestamp),
                eventSequenceGenerationConfigs = eventSequenceGenerationConfigs,
            )
    }
}
