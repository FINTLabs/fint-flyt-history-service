package no.fintlabs.consumers;

import no.fintlabs.InstanceFlowHeadersEmbeddableMapper;
import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventConsumerFactoryService;
import no.fintlabs.kafka.OriginHeaderProducerInterceptor;
import no.fintlabs.kafka.event.error.ErrorCollection;
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters;
import no.fintlabs.model.Error;
import no.fintlabs.model.Event;
import no.fintlabs.model.EventType;
import no.fintlabs.repositories.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.stream.Collectors;

import static no.fintlabs.EventTopicNames.*;

@Configuration
public class ErrorEventConsumerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowErrorEventConsumerFactoryService instanceFlowErrorEventConsumerFactoryService;
    private final InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper;

    public ErrorEventConsumerConfiguration(
            EventRepository eventRepository,
            InstanceFlowErrorEventConsumerFactoryService instanceFlowErrorEventConsumerFactoryService,
            InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper
    ) {
        this.eventRepository = eventRepository;
        this.instanceFlowErrorEventConsumerFactoryService = instanceFlowErrorEventConsumerFactoryService;
        this.instanceFlowHeadersEmbeddableMapper = instanceFlowHeadersEmbeddableMapper;
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceReceivalErrorEventConsumer() {
        return createErrorEventListener(INSTANCE_RECEIVAL_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceRegistrationErrorEventConsumer() {
        return createErrorEventListener(INSTANCE_REGISTRATION_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceRetryRequestErrorEventConsumer() {
        return createErrorEventListener(INSTANCE_RETRY_REQUEST_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceMappingErrorEventConsumer() {
        return createErrorEventListener(INSTANCE_MAPPING_ERROR);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, ErrorCollection> instanceDispatchingErrorEventConsumer() {
        return createErrorEventListener(INSTANCE_DISPATCHING_ERROR);
    }

    private ConcurrentMessageListenerContainer<String, ErrorCollection> createErrorEventListener(String errorEventName) {
        return instanceFlowErrorEventConsumerFactoryService.createRecordFactory(
                instanceFlowConsumerRecord -> {
                    Event event = new Event();
                    event.setInstanceFlowHeaders(
                            instanceFlowHeadersEmbeddableMapper.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    event.setName(errorEventName);
                    event.setType(EventType.ERROR);
                    event.setTimestamp(
                            Instant.ofEpochMilli(instanceFlowConsumerRecord.getConsumerRecord().timestamp())
                                    .atOffset(ZoneOffset.UTC)
                    );
                    event.setErrors(mapToErrorEntities(instanceFlowConsumerRecord.getConsumerRecord().value()));
                    event.setApplicationId(new String(
                            instanceFlowConsumerRecord.getConsumerRecord()
                                    .headers()
                                    .lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER)
                                    .value(),
                            StandardCharsets.UTF_8
                    ));
                    eventRepository.save(event);
                }
        ).createContainer(createErrorEventTopicNameParameters(errorEventName));
    }

    private Collection<Error> mapToErrorEntities(ErrorCollection errorCollection) {
        return errorCollection.getErrors().stream().map(this::mapToErrorEntity).collect(Collectors.toList());
    }

    private Error mapToErrorEntity(no.fintlabs.kafka.event.error.Error errorFromEvent) {
        Error error = new Error();
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
