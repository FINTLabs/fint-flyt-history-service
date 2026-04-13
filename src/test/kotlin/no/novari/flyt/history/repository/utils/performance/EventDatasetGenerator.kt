package no.novari.flyt.history.repository.utils.performance

import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.history.repository.utils.BatchPersister
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Random
import java.util.UUID
import kotlin.math.log10
import kotlin.math.max

class EventDatasetGenerator(
    private val batchPersister: BatchPersister<EventEntity>,
    seed: Long,
    private val eventsPerPersistOperation: Int,
) {
    private val random = Random(seed)
    private var instanceIdCounter = 0L

    fun generateAndPersistEvents(eventGenerationConfigs: List<EventGenerationConfig>) {
        var largestNumberOfEventsPerSequence = 0
        val totalNumberOfEvents =
            eventGenerationConfigs
                .flatMap(EventGenerationConfig::eventSequenceGenerationConfigs)
                .sumOf { eventGenerationConfig ->
                    (eventGenerationConfig.eventSequence.size * eventGenerationConfig.numberOfSequences).also {
                        largestNumberOfEventsPerSequence = max(largestNumberOfEventsPerSequence, it)
                    }
                }
        val totalNumberOfConfigs = eventGenerationConfigs.size
        var totalNumberOfEventsGeneratedAndPersisted = 0

        log.info("Generating and persisting {} events from {} configs", totalNumberOfEvents, totalNumberOfConfigs)
        val totalTimer = Timer.start()

        eventGenerationConfigs.forEachIndexed { configIndex, eventGenerationConfig ->
            val numberOfEventsFromConfig =
                eventGenerationConfig.eventSequenceGenerationConfigs.sumOf { config ->
                    config.eventSequence.size * config.numberOfSequences
                }
            val configNumber = configIndex + 1
            log.debug("Generating and persisting {} events from config {}", numberOfEventsFromConfig, configNumber)

            val configTimer = Timer.start()
            generateAndPersistEvents(eventGenerationConfig)
            val totalElapsedTime = totalTimer.elapsedTime
            val configElapsedTime = configTimer.elapsedTime

            val numberOfEventsPerSecondForConfig =
                numberOfEventsFromConfig.toDouble() * 1000 / configElapsedTime.toMillis()
            totalNumberOfEventsGeneratedAndPersisted += numberOfEventsFromConfig
            val percentageOfTotalEventsProcessed =
                totalNumberOfEventsGeneratedAndPersisted.toDouble() * 100 / totalNumberOfEvents
            val numberOfEventsPerSecondTotal =
                totalNumberOfEventsGeneratedAndPersisted.toDouble() * 1000 / totalElapsedTime.toMillis()
            val totalRemainingEvents = totalNumberOfEvents - totalNumberOfEventsGeneratedAndPersisted
            val estimatedRemainingTime =
                configElapsedTime
                    .dividedBy(numberOfEventsFromConfig.toLong())
                    .multipliedBy(totalRemainingEvents.toLong())

            log.info(
                "Config {}/{}: {} events in {} ({}/s) || Total: {}/{} ({}%) events in {} ({}/s) || Estimated remaining time: {}",
                String.format("%,${getNumberOfDigits(totalNumberOfConfigs)}d", configNumber),
                String.format("%,d", totalNumberOfConfigs),
                String.format("%,${getNumberOfDigits(largestNumberOfEventsPerSequence)}d", numberOfEventsFromConfig),
                DurationFormatter.formatDuration(configElapsedTime),
                String.format("%,6.2f", numberOfEventsPerSecondForConfig),
                String.format("%,${getNumberOfDigits(totalNumberOfEvents)}d", totalNumberOfEventsGeneratedAndPersisted),
                String.format("%,d", totalNumberOfEvents),
                String.format("%,.2f", percentageOfTotalEventsProcessed),
                DurationFormatter.formatDuration(totalElapsedTime),
                String.format("%,6.2f", numberOfEventsPerSecondTotal),
                DurationFormatter.formatDuration(estimatedRemainingTime),
            )
        }

        val elapsedTime = totalTimer.elapsedTime
        log.info(
            "Generated and persisted {} events in {} ({}/s)",
            totalNumberOfEvents,
            DurationFormatter.formatDuration(elapsedTime),
            String.format("%.2f", totalNumberOfEvents.toDouble() * 1000 / elapsedTime.toMillis()),
        )
    }

    fun generateEvent(
        eventCategory: EventCategory,
        headers: InstanceFlowHeadersEmbeddable,
        timestamp: OffsetDateTime,
    ): EventEntity {
        return EventEntity
            .builder()
            .instanceFlowHeaders(headers)
            .name(eventCategory.eventName)
            .timestamp(timestamp)
            .type(eventCategory.type)
            .build()
    }

    private fun getNumberOfDigits(value: Int): Int {
        val valueLog10 = log10(value.toDouble()).toInt()
        val numberOfCommas = valueLog10 / 3
        return log10(value.toDouble()).toInt() + numberOfCommas + 1
    }

    private fun generateAndPersistEvents(eventGenerationConfig: EventGenerationConfig) {
        val events = mutableListOf<EventEntity>()
        generateEvents(eventGenerationConfig) { eventEntity ->
            events += eventEntity
            if (events.size == eventsPerPersistOperation) {
                batchPersister.persistInBatches(events.toList())
                events.clear()
            }
        }
        if (events.isNotEmpty()) {
            batchPersister.persistInBatches(events.toList())
            events.clear()
        }
    }

    private fun generateEvents(
        eventGenerationConfig: EventGenerationConfig,
        eventEntityConsumer: (EventEntity) -> Unit,
    ) {
        eventGenerationConfig.eventSequenceGenerationConfigs.forEach { sequenceGenerationConfig ->
            repeat(sequenceGenerationConfig.numberOfSequences) {
                val sequenceEventOrder = sequenceGenerationConfig.eventSequence
                val sequenceSourceApplicationInstanceId =
                    sequenceGenerationConfig.sourceApplicationInstanceIdOverride ?: generateStringOfMaxNLength(20)
                val sequenceMinMaxTimestamps =
                    generateNOrderedOffsetDateTimeInRange(
                        eventGenerationConfig.minTimestamp,
                        eventGenerationConfig.maxTimestamp,
                        2,
                    )
                val sequenceEventTimestamps =
                    generateNOrderedOffsetDateTimeInRange(
                        sequenceMinMaxTimestamps[0],
                        sequenceMinMaxTimestamps[1],
                        sequenceEventOrder.size,
                    )

                var currentSequenceCorrelationId: UUID? = null
                var currentSequenceInstanceId: Long? = null

                sequenceEventOrder.forEachIndexed { index, eventCategory ->
                    if (eventCategory == EventCategory.INSTANCE_REGISTERED) {
                        currentSequenceInstanceId = ++instanceIdCounter
                    }
                    if (eventCategory == EventCategory.INSTANCE_RECEIVED ||
                        eventCategory == EventCategory.INSTANCE_REQUESTED_FOR_RETRY
                    ) {
                        currentSequenceCorrelationId = UUID.randomUUID()
                    }

                    val headers =
                        InstanceFlowHeadersEmbeddable
                            .builder()
                            .sourceApplicationId(eventGenerationConfig.sourceApplicationId)
                            .sourceApplicationIntegrationId(eventGenerationConfig.sourceApplicationIntegrationId)
                            .sourceApplicationInstanceId(sequenceSourceApplicationInstanceId)
                            .correlationId(currentSequenceCorrelationId)
                            .integrationId(eventGenerationConfig.integrationId)
                            .instanceId(currentSequenceInstanceId)
                            .archiveInstanceId(
                                if (eventCategory == EventCategory.INSTANCE_DISPATCHED) {
                                    generateStringOfMaxNLength(10)
                                } else {
                                    null
                                },
                            ).build()

                    eventEntityConsumer(
                        generateEvent(
                            eventCategory,
                            headers,
                            sequenceEventTimestamps[index],
                        ),
                    )
                }
            }
        }
    }

    private fun generateNOrderedOffsetDateTimeInRange(
        minTimestamp: OffsetDateTime,
        maxTimestamp: OffsetDateTime,
        n: Int,
    ): List<OffsetDateTime> {
        val millisBetweenMinAndMax = minTimestamp.until(maxTimestamp, ChronoUnit.MILLIS)
        if (minTimestamp.isEqual(maxTimestamp)) {
            return List(n) { minTimestamp }
        }
        return (0 until n)
            .map { random.nextLong(millisBetweenMinAndMax) }
            .sorted()
            .map { minTimestamp.plus(it, ChronoUnit.MILLIS) }
    }

    private fun generateStringOfMaxNLength(n: Int): String {
        return generateSequence {
            random.nextInt(123 - 48) + 48
        }.filter { (it <= 57 || it >= 65) && (it <= 90 || it >= 97) }
            .take(n)
            .fold(StringBuilder(), StringBuilder::appendCodePoint)
            .toString()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(EventDatasetGenerator::class.java)
    }
}
