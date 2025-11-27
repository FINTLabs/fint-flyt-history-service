package no.novari.flyt.history.repository.utils.performance;

import lombok.extern.slf4j.Slf4j;
import no.novari.flyt.history.model.event.EventCategory;
import no.novari.flyt.history.repository.entities.EventEntity;
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable;
import no.novari.flyt.history.repository.utils.BatchPersister;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static no.novari.flyt.history.repository.utils.performance.DurationFormatter.formatDuration;


@Slf4j
public class EventDatasetGenerator {
    private final BatchPersister<EventEntity> batchPersister;
    private final int eventsPerPersistOperation;
    private final Random random;
    private long instanceIdCounter = 0;

    public EventDatasetGenerator(
            BatchPersister<EventEntity> batchPersister,
            long seed,
            int eventsPerPersistOperation
    ) {
        this.batchPersister = batchPersister;
        this.eventsPerPersistOperation = eventsPerPersistOperation;
        this.random = new Random(seed);
    }

    public void generateAndPersistEvents(List<EventGenerationConfig> eventGenerationConfigs) {
        AtomicInteger largestNumberOfEventsPerSequence = new AtomicInteger(0);
        int totalNumberOfEvents = eventGenerationConfigs.stream()
                .map(EventGenerationConfig::getEventSequenceGenerationConfigs)
                .flatMap(Collection::stream)
                .mapToInt(eventGenerationConfig -> eventGenerationConfig.getEventSequence().size()
                                                   * eventGenerationConfig.getNumberOfSequences()
                )
                .peek(numberOfEvents -> largestNumberOfEventsPerSequence.getAndUpdate(
                        i -> Math.max(i, numberOfEvents)
                ))
                .sum();
        int totalNumberOfConfigs = eventGenerationConfigs.size();
        AtomicInteger totalNumberOfEventsGeneratedAndPersisted = new AtomicInteger();
        log.info("Generating and persisting {} events from {} configs", totalNumberOfEvents, totalNumberOfConfigs);
        Timer totalTimer = Timer.start();
        IntStream.range(0, totalNumberOfConfigs).forEach(configIndex -> {
                    EventGenerationConfig eventGenerationConfig = eventGenerationConfigs.get(configIndex);
                    int numberOfEventsFromConfig = eventGenerationConfig.getEventSequenceGenerationConfigs()
                            .stream()
                            .mapToInt(eventSequenceGenerationConfig ->
                                    eventSequenceGenerationConfig.getEventSequence().size()
                                    * eventSequenceGenerationConfig.getNumberOfSequences()
                            )
                            .sum();
                    int configNumber = configIndex + 1;
                    log.debug("Generating and persisting {} events from config {}",
                            numberOfEventsFromConfig,
                            configNumber
                    );

                    Timer configTimer = Timer.start();
                    generateAndPersistEvents(eventGenerationConfig);
                    Duration totalElapsedTime = totalTimer.getElapsedTime();
                    Duration configElapsedTime = configTimer.getElapsedTime();

                    double numberOfEventsPerSecondForConfig =
                            (double) numberOfEventsFromConfig * 1000 / configElapsedTime.toMillis();
                    int totalNumberOfEventsGeneratedAndPersistedValue =
                            totalNumberOfEventsGeneratedAndPersisted.accumulateAndGet(
                                    numberOfEventsFromConfig,
                                    Math::addExact
                            );
                    double percentageOfTotalEventsProcessed =
                            (double) totalNumberOfEventsGeneratedAndPersistedValue * 100 / totalNumberOfEvents;
                    double numberOfEventsPerSecondTotal =
                            (double) totalNumberOfEventsGeneratedAndPersistedValue * 1000 / totalElapsedTime.toMillis();
                    int totalRemainingEvents = totalNumberOfEvents - totalNumberOfEventsGeneratedAndPersistedValue;
                    Duration estimatedRemainingTime = configElapsedTime
                            .dividedBy(numberOfEventsFromConfig)
                            .multipliedBy(totalRemainingEvents);
                    log.info("Config {}/{}: {} events in {} ({}/s) || Total: {}/{} ({}%) events in {} ({}/s) || Estimated remaining time: {}",
                            String.format("%," + getNumberOfDigits(totalNumberOfConfigs) + "d", configNumber),
                            String.format("%,d", totalNumberOfConfigs),
                            String.format("%," + getNumberOfDigits(largestNumberOfEventsPerSequence.get()) + "d", numberOfEventsFromConfig),
                            formatDuration(configElapsedTime),
                            String.format("%,6.2f", numberOfEventsPerSecondForConfig),
                            String.format("%," + getNumberOfDigits(totalNumberOfEvents) + "d", totalNumberOfEventsGeneratedAndPersistedValue),
                            String.format("%,d", totalNumberOfEvents),
                            String.format("%,.2f", percentageOfTotalEventsProcessed),
                            formatDuration(totalElapsedTime),
                            String.format("%,6.2f", numberOfEventsPerSecondTotal),
                            formatDuration(estimatedRemainingTime)
                    );
                }
        );
        Duration elapsedTime = totalTimer.getElapsedTime();
        log.info("Generated and persisted {} events in {} ({}/s)",
                totalNumberOfEvents,
                formatDuration(elapsedTime),
                String.format("%.2f", (double) totalNumberOfEvents * 1000 / elapsedTime.toMillis())
        );
    }

    private int getNumberOfDigits(int value) {
        int log10 = (int) Math.log10(value);
        int numOfCommas = (int) (double) log10 / 3;
        return (int) (Math.log10(value) + numOfCommas + 1);
    }

    private void generateAndPersistEvents(EventGenerationConfig eventGenerationConfig) {
        List<EventEntity> events = new ArrayList<>();
        generateEvents(
                eventGenerationConfig,
                eventEntity -> {
                    events.add(eventEntity);
                    if (events.size() == eventsPerPersistOperation) {
                        batchPersister.persistInBatches(
                                events
                        );
                        events.clear();
                    }
                }
        );
        if (!events.isEmpty()) {
            batchPersister.persistInBatches(events);
            events.clear();
        }
    }

    private void generateEvents(
            EventGenerationConfig eventGenerationConfig,
            Consumer<EventEntity> eventEntityConsumer
    ) {
        for (EventSequenceGenerationConfig sequenceGenerationConfig : eventGenerationConfig.getEventSequenceGenerationConfigs()) {
            for (int i = 0; i < sequenceGenerationConfig.getNumberOfSequences(); i++) {
                List<EventCategory> sequenceEventOrder = sequenceGenerationConfig.getEventSequence();
                String sequenceSourceApplicationInstanceId =
                        Optional.ofNullable(sequenceGenerationConfig.getSourceApplicationInstanceIdOverride())
                                .orElse(generateStringOfMaxNLength(20));
                List<OffsetDateTime> sequenceMinMaxTimestamps = generateNOrderedOffsetDateTimeInRange(
                        eventGenerationConfig.getMinTimestamp(),
                        eventGenerationConfig.getMaxTimestamp(),
                        2
                );
                List<OffsetDateTime> sequenceEventTimestamps = generateNOrderedOffsetDateTimeInRange(
                        sequenceMinMaxTimestamps.get(0),
                        sequenceMinMaxTimestamps.get(1),
                        sequenceEventOrder.size()
                );
                UUID currentSequenceCorrelationId = null;
                Long currentSequenceInstanceId = null;
                for (int j = 0; j < sequenceEventOrder.size(); j++) {
                    EventCategory eventCategory = sequenceEventOrder.get(j);
                    if (eventCategory == EventCategory.INSTANCE_REGISTERED) {
                        currentSequenceInstanceId = ++instanceIdCounter;
                    }
                    if (eventCategory == EventCategory.INSTANCE_RECEIVED || eventCategory == EventCategory.INSTANCE_REQUESTED_FOR_RETRY) {
                        currentSequenceCorrelationId = UUID.randomUUID();
                    }

                    InstanceFlowHeadersEmbeddable headers = InstanceFlowHeadersEmbeddable
                            .builder()
                            .sourceApplicationId(eventGenerationConfig.getSourceApplicationId())
                            .sourceApplicationIntegrationId(eventGenerationConfig.getSourceApplicationIntegrationId())
                            .sourceApplicationInstanceId(sequenceSourceApplicationInstanceId)
                            .correlationId(currentSequenceCorrelationId)
                            .integrationId(eventGenerationConfig.getIntegrationId())
                            .instanceId(currentSequenceInstanceId)
                            .archiveInstanceId(
                                    eventCategory == EventCategory.INSTANCE_DISPATCHED
                                            ? generateStringOfMaxNLength(10)
                                            : null
                            )
                            .build();

                    OffsetDateTime eventTimestamp = sequenceEventTimestamps.get(j);
                    eventEntityConsumer.accept(
                            generateEvent(
                                    eventCategory,
                                    headers,
                                    eventTimestamp
                            )
                    );
                }
            }
        }
    }

    public EventEntity generateEvent(
            EventCategory eventCategory,
            InstanceFlowHeadersEmbeddable headers,
            OffsetDateTime timestamp
    ) {
        return EventEntity
                .builder()
                .instanceFlowHeaders(headers)
                .name(eventCategory.getEventName())
                .timestamp(timestamp)
                .type(eventCategory.getType())
                .build();
    }

    private List<OffsetDateTime> generateNOrderedOffsetDateTimeInRange(OffsetDateTime minTimestamp, OffsetDateTime maxTimestamp, int n) {
        long millisBetweenMinAndMax = minTimestamp.until(maxTimestamp, ChronoUnit.MILLIS);
        if (minTimestamp.isEqual(maxTimestamp)) {
            return IntStream.range(0, n)
                    .mapToObj(i -> minTimestamp)
                    .toList();
        }
        return IntStream.range(0, n)
                .mapToLong(i -> random.nextLong(millisBetweenMinAndMax))
                .sorted()
                .mapToObj(l -> minTimestamp.plus(l, ChronoUnit.MILLIS))
                .toList();
    }

    private String generateStringOfMaxNLength(int n) {
        return random.ints(48, 123)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(n)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
