package no.fintlabs.kafka;

import lombok.AllArgsConstructor;
import no.fintlabs.flyt.kafka.event.InstanceFlowEventConsumerFactoryService;
import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventConsumerFactoryService;
import no.fintlabs.kafka.event.error.ErrorCollection;
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters;
import no.fintlabs.kafka.event.topic.EventTopicNameParameters;
import no.fintlabs.mapping.InstanceFlowHeadersMappingService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import no.fintlabs.repository.EventRepository;
import no.fintlabs.repository.entities.ErrorEntity;
import no.fintlabs.repository.entities.EventEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@AllArgsConstructor
public class EventListenerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowErrorEventConsumerFactoryService instanceFlowErrorEventConsumerFactoryService;
    private final InstanceFlowEventConsumerFactoryService instanceFlowEventConsumerFactoryService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;

    @Bean
    public List<? extends ConcurrentMessageListenerContainer<String, ?>> eventListeners() {
        return Arrays.stream(EventCategory.values())
                .map(this::createEventListener)
                .toList();
    }

    private ConcurrentMessageListenerContainer<String, ?> createEventListener(EventCategory eventCategory) {
        return switch (eventCategory.getType()) {
            case INFO -> createInfoEventListener(eventCategory);
            case ERROR -> createErrorEventListener(eventCategory);
        };
    }

    private ConcurrentMessageListenerContainer<String, Object> createInfoEventListener(EventCategory category) {
        return instanceFlowEventConsumerFactoryService.createRecordFactory(
                Object.class,
                instanceFlowConsumerRecord -> {
                    EventEntity eventEntity = new EventEntity();
                    eventEntity.setInstanceFlowHeaders(
                            instanceFlowHeadersMappingService.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    eventEntity.setName(category.getName());
                    eventEntity.setType(EventType.INFO);
                    eventEntity.setTimestamp(
                            Instant.ofEpochMilli(instanceFlowConsumerRecord.getConsumerRecord().timestamp())
                                    .atOffset(ZoneOffset.UTC)
                    );
                    eventEntity.setApplicationId(new String(
                            instanceFlowConsumerRecord.getConsumerRecord()
                                    .headers()
                                    .lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER)
                                    .value(),
                            StandardCharsets.UTF_8
                    ));
                    eventRepository.save(eventEntity);
                }
        ).createContainer(
                EventTopicNameParameters.builder()
                        .eventName(category.getName())
                        .build()
        );
    }

    private ConcurrentMessageListenerContainer<String, ErrorCollection> createErrorEventListener(EventCategory eventCategory) {
        return instanceFlowErrorEventConsumerFactoryService.createRecordFactory(
                instanceFlowConsumerRecord -> {
                    EventEntity eventEntity = new EventEntity();
                    eventEntity.setInstanceFlowHeaders(
                            instanceFlowHeadersMappingService.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    eventEntity.setName(eventCategory.getName());
                    eventEntity.setType(EventType.ERROR);
                    eventEntity.setTimestamp(
                            Instant.ofEpochMilli(instanceFlowConsumerRecord.getConsumerRecord().timestamp())
                                    .atOffset(ZoneOffset.UTC)
                    );
                    eventEntity.setErrors(mapToErrorEntities(instanceFlowConsumerRecord.getConsumerRecord().value()));
                    eventEntity.setApplicationId(new String(
                            instanceFlowConsumerRecord.getConsumerRecord()
                                    .headers()
                                    .lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER)
                                    .value(),
                            StandardCharsets.UTF_8
                    ));
                    eventRepository.save(eventEntity);
                }
        ).createContainer(createErrorEventTopicNameParameters(eventCategory.getName()));
    }

    private Collection<ErrorEntity> mapToErrorEntities(ErrorCollection errorCollection) {
        return errorCollection.getErrors().stream().map(this::mapToErrorEntity).collect(Collectors.toList());
    }

    private ErrorEntity mapToErrorEntity(no.fintlabs.kafka.event.error.Error errorFromEvent) {
        ErrorEntity error = new ErrorEntity();
        error.setErrorCode(errorFromEvent.getErrorCode());
        error.setArgs(errorFromEvent.getArgs());
        return error;
    }

    private ErrorEventTopicNameParameters createErrorEventTopicNameParameters(String errorEventName) {
        return ErrorEventTopicNameParameters.builder()
                .errorEventName(errorEventName)
                .build();
    }
}
