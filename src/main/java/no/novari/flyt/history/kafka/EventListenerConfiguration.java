package no.novari.flyt.history.kafka;

import lombok.AllArgsConstructor;
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService;
import no.novari.flyt.history.model.event.EventCategory;
import no.novari.flyt.history.model.event.EventType;
import no.novari.flyt.history.repository.EventRepository;
import no.novari.flyt.history.repository.entities.ErrorEntity;
import no.novari.flyt.history.repository.entities.EventEntity;
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService;
import no.novari.flyt.kafka.model.Error;
import no.novari.flyt.kafka.model.ErrorCollection;
import no.novari.kafka.OriginHeaderProducerInterceptor;
import no.novari.kafka.consuming.ErrorHandlerConfiguration;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.consuming.ListenerConfiguration;
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters;
import no.novari.kafka.topic.name.EventTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
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
    private final InstanceFlowListenerFactoryService instanceFlowListenerFactoryService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    private final ErrorHandlerFactory errorHandlerFactory;

    @Bean
    public List<? extends ConcurrentMessageListenerContainer<String, ?>> eventListeners() {
        return Arrays.stream(EventCategory.values())
                .filter(EventCategory::isCreateKafkaListener)
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
        return instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                Object.class,
                instanceFlowConsumerRecord -> {
                    EventEntity eventEntity = new EventEntity();
                    eventEntity.setInstanceFlowHeaders(
                            instanceFlowHeadersMappingService.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    eventEntity.setName(category.getEventName());
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
                },
                ListenerConfiguration
                        .stepBuilder()
                        .groupIdApplicationDefault()
                        .maxPollRecordsKafkaDefault()
                        .maxPollIntervalKafkaDefault()
                        .continueFromPreviousOffsetOnAssignment()
                        .build(),
                errorHandlerFactory.createErrorHandler(
                        ErrorHandlerConfiguration
                                .stepBuilder()
                                .noRetries()
                                .skipFailedRecords()
                                .build()
                )
        ).createContainer(
                EventTopicNameParameters.builder()
                        .eventName(category.getEventName())
                        .topicNamePrefixParameters(
                                TopicNamePrefixParameters
                                        .stepBuilder()
                                        .orgIdApplicationDefault()
                                        .domainContextApplicationDefault()
                                        .build()
                        )
                        .build()
        );
    }

    private ConcurrentMessageListenerContainer<String, ErrorCollection> createErrorEventListener(EventCategory eventCategory) {
        return instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                ErrorCollection.class,
                instanceFlowConsumerRecord -> {
                    EventEntity eventEntity = new EventEntity();
                    eventEntity.setInstanceFlowHeaders(
                            instanceFlowHeadersMappingService.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    eventEntity.setName(eventCategory.getEventName());
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
                },
                ListenerConfiguration
                        .stepBuilder()
                        .groupIdApplicationDefault()
                        .maxPollRecordsKafkaDefault()
                        .maxPollIntervalKafkaDefault()
                        .continueFromPreviousOffsetOnAssignment()
                        .build(),
                errorHandlerFactory.createErrorHandler(
                        ErrorHandlerConfiguration
                                .stepBuilder()
                                .noRetries()
                                .skipFailedRecords()
                                .build()
                )
        ).createContainer(createErrorEventTopicNameParameters(eventCategory.getEventName()));
    }

    private Collection<ErrorEntity> mapToErrorEntities(ErrorCollection errorCollection) {
        return errorCollection.getErrors().stream().map(this::mapToErrorEntity).collect(Collectors.toList());
    }

    private ErrorEntity mapToErrorEntity(Error errorFromEvent) {
        ErrorEntity error = new ErrorEntity();
        error.setErrorCode(errorFromEvent.getErrorCode());
        error.setArgs(errorFromEvent.getArgs());
        return error;
    }

    private ErrorEventTopicNameParameters createErrorEventTopicNameParameters(String errorEventName) {
        return ErrorEventTopicNameParameters.builder()
                .errorEventName(errorEventName)
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters
                                .stepBuilder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .build();
    }
}
