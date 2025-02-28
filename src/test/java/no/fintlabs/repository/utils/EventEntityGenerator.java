package no.fintlabs.repository.utils;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.hibernate.SessionFactory;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class EventEntityGenerator {
    private final Random random;
    private long instanceIdCounter = 0;
    private final String tableName;
    private final String entityPropertyColumnNames;
    private final Function<EventEntity, List<Object>> getEntityPropertyValues;

    public EventEntityGenerator(EntityManagerFactory entityManagerFactory) {
        this.random = new Random(42);
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        MetamodelImplementor metamodel = (MetamodelImplementor) sessionFactory.getMetamodel();
        AbstractEntityPersister entityPersister =
                (AbstractEntityPersister) metamodel.entityPersister(EventEntity.class.getName());

        tableName = entityPersister.getTableName();
        entityPropertyColumnNames = Arrays.stream(entityPersister.getPropertyNames())
                .map(name -> entityPersister.getPropertyColumnNames(name)[0])
                .collect(Collectors.joining(", "));
        getEntityPropertyValues = eventEntity ->
                Arrays.stream(entityPersister.getPropertyValues(eventEntity)).toList();
    }

    // TODO 07/02/2025 eivindmorch: Fix
    public void generateEventsInitScript(
            String name,
            long sourceApplicationId,
            String sourceApplicationIntegrationId,
            long integrationId,
            OffsetDateTime minTimestamp,
            OffsetDateTime maxTimestamp,
            List<SequenceGenerationConfig> sequenceGenerationConfigs
    ) {
        //BufferedWriter writer = new BufferedWriter(new FileWriter("db/" + name + ".sql", true));
        generateEvents(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                integrationId,
                minTimestamp,
                maxTimestamp,
                sequenceGenerationConfigs,
                eventEntity -> {
                    // try {
                    log.info(generateInsertStatement(eventEntity));
                    //writer.write((generateInsertStatement(eventEntity)));
                    //writer.newLine();
                    //} catch (IOException e) {
                    //     throw new RuntimeException(e);
                    // }
                }
        );
    }

    private String generateInsertStatement(EventEntity eventEntity) {
        return "INSERT INTO " + tableName + " (" + entityPropertyColumnNames + ")" +
               " VALUES (" + getEntityPropertyValues.apply(eventEntity) + ")";
    }

    public List<EventEntity> generateEvents(
            long sourceApplicationId,
            String sourceApplicationIntegrationId,
            long integrationId,
            OffsetDateTime minTimestamp,
            OffsetDateTime maxTimestamp,
            List<SequenceGenerationConfig> sequenceGenerationConfigs
    ) {
        List<EventEntity> events = new ArrayList<>();
        generateEvents(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                integrationId,
                minTimestamp,
                maxTimestamp,
                sequenceGenerationConfigs,
                events::add
        );
        return events;
    }

    private void generateEvents(
            long sourceApplicationId,
            String sourceApplicationIntegrationId,
            long integrationId,
            OffsetDateTime minTimestamp,
            OffsetDateTime maxTimestamp,
            List<SequenceGenerationConfig> sequenceGenerationConfigs,
            Consumer<EventEntity> eventEntityConsumer
    ) {
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
