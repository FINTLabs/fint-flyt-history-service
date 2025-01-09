package no.fintlabs.repository.utils;

import no.fintlabs.model.event.EventCategory;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

public class EventEntityGenerator {

    private final Random random;
    private long instanceIdCounter = 0;

    public EventEntityGenerator(Long seed) {
        this.random = new Random(seed);
    }

    public List<EventEntity> generateEvents(
            long sourceApplicationId,
            String sourceApplicationIntegrationId,
            long integrationId,
            OffsetDateTime minTimestamp,
            OffsetDateTime maxTimestamp,
            List<SequenceGenerationConfig> sequenceGenerationConfigs
    ) {
        List<EventEntity> eventEntities = new ArrayList<>();

        for (SequenceGenerationConfig sequenceGenerationConfig : sequenceGenerationConfigs) {

            for (int i = 0; i < sequenceGenerationConfig.getNumberOfSequences(); i++) {
                List<EventCategory> sequenceEventOrder = sequenceGenerationConfig.getEventSequence();
                String sequenceSourceApplicationInstanceId =
                        Optional.ofNullable(sequenceGenerationConfig.getSourceApplicationInstanceIdOverride())
                                .orElse(generateStringOfMaxNLength(10));
                List<OffsetDateTime> sequenceMinMaxTimestamps = generateNOrderedOffsetDateTimeInRange(
                        minTimestamp,
                        maxTimestamp,
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
                            .sourceApplicationId(sourceApplicationId)
                            .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                            .sourceApplicationInstanceId(sequenceSourceApplicationInstanceId)
                            .correlationId(currentSequenceCorrelationId)
                            .integrationId(integrationId)
                            .instanceId(currentSequenceInstanceId)
                            .archiveInstanceId(
                                    eventCategory == EventCategory.INSTANCE_DISPATCHED
                                            ? generateStringOfMaxNLength(10)
                                            : null
                            )
                            .build();

                    OffsetDateTime eventTimestamp = sequenceEventTimestamps.get(j);
                    eventEntities.add(generateEvent(
                                    eventCategory,
                                    headers,
                                    eventTimestamp
                            )
                    );
                }
            }
        }
        return eventEntities;
    }

    public EventEntity generateEvent(
            EventCategory eventCategory,
            InstanceFlowHeadersEmbeddable headers,
            OffsetDateTime timestamp
    ) {
        return EventEntity
                .builder()
                .instanceFlowHeaders(headers)
                .name(eventCategory.getName())
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
