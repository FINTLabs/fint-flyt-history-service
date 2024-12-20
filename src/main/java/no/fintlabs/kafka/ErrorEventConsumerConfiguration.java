package no.fintlabs.kafka;

import no.fintlabs.EventRepository;
import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventConsumerFactoryService;
import no.fintlabs.kafka.event.error.ErrorCollection;
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters;
import no.fintlabs.mapping.InstanceFlowHeadersMappingService;
import no.fintlabs.model.entities.ErrorEntity;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.eventinfo.EventInfo;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.stream.Collectors;

@Configuration
public class ErrorEventConsumerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowErrorEventConsumerFactoryService instanceFlowErrorEventConsumerFactoryService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;

    public ErrorEventConsumerConfiguration(
            EventRepository eventRepository,
            InstanceFlowErrorEventConsumerFactoryService instanceFlowErrorEventConsumerFactoryService,
            InstanceFlowHeadersMappingService instanceFlowHeadersMappingService
    ) {
        this.eventRepository = eventRepository;
        this.instanceFlowErrorEventConsumerFactoryService = instanceFlowErrorEventConsumerFactoryService;
        this.instanceFlowHeadersMappingService = instanceFlowHeadersMappingService;
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceReceivalErrorEventConsumer() {
        return createErrorEventListener(InstanceStatusEvent.INSTANCE_RECEIVAL_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceRegistrationErrorEventConsumer() {
        return createErrorEventListener(InstanceStatusEvent.INSTANCE_REGISTRATION_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceRetryRequestErrorEventConsumer() {
        return createErrorEventListener(InstanceStatusEvent.INSTANCE_RETRY_REQUEST_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceMappingErrorEventConsumer() {
        return createErrorEventListener(InstanceStatusEvent.INSTANCE_MAPPING_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceDispatchingErrorEventConsumer() {
        return createErrorEventListener(InstanceStatusEvent.INSTANCE_DISPATCHING_ERROR);
    }

    private ConcurrentMessageListenerContainer<String, ErrorCollection> createErrorEventListener(EventInfo eventInfo) {
        return instanceFlowErrorEventConsumerFactoryService.createRecordFactory(
                instanceFlowConsumerRecord -> {
                    EventEntity eventEntity = new EventEntity();
                    eventEntity.setInstanceFlowHeaders(
                            instanceFlowHeadersMappingService.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    eventEntity.setName(eventInfo.getName());
                    eventEntity.setType(eventInfo.getType());
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
        ).createContainer(createErrorEventTopicNameParameters(eventInfo.getName()));
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
