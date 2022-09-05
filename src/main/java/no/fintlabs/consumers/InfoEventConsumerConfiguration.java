package no.fintlabs.consumers;

import no.fintlabs.InstanceFlowHeadersEmbeddableMapper;
import no.fintlabs.flyt.kafka.event.InstanceFlowEventConsumerFactoryService;
import no.fintlabs.kafka.OriginHeaderProducerInterceptor;
import no.fintlabs.kafka.event.topic.EventTopicNameParameters;
import no.fintlabs.model.Event;
import no.fintlabs.model.EventType;
import no.fintlabs.repositories.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
public class InfoEventConsumerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowEventConsumerFactoryService instanceFlowEventConsumerFactoryService;
    private final InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper;


    public InfoEventConsumerConfiguration(
            EventRepository eventRepository,
            InstanceFlowEventConsumerFactoryService instanceFlowEventConsumerFactoryService,
            InstanceFlowHeadersEmbeddableMapper skjemaEventHeadersMapper) {
        this.eventRepository = eventRepository;
        this.instanceFlowEventConsumerFactoryService = instanceFlowEventConsumerFactoryService;
        this.instanceFlowHeadersEmbeddableMapper = skjemaEventHeadersMapper;
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceReceivedEventConsumer() {
        return createInfoEventListener("instance-received");
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceRegisteredEventConsumer() {
        return createInfoEventListener("instance-registered");
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceRequestedForRetry() {
        return createInfoEventListener("instance-requested-for-retry");
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> caseCreatedEventConsumer() {
        return createInfoEventListener("case-created");
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> caseDispatchedEventConsumer() {
        return createInfoEventListener("case-dispatched");
    }

    private ConcurrentMessageListenerContainer<String, Object> createInfoEventListener(String eventName) {
        return instanceFlowEventConsumerFactoryService.createFactory(
                Object.class,
                instanceFlowConsumerRecord -> {
                    Event event = new Event();
                    event.setInstanceFlowHeaders(
                            instanceFlowHeadersEmbeddableMapper.toEmbeddable(instanceFlowConsumerRecord.getInstanceFlowHeaders())
                    );
                    event.setName(eventName);
                    event.setType(EventType.INFO);
                    event.setTimestamp(
                            Instant.ofEpochMilli(instanceFlowConsumerRecord.getConsumerRecord().timestamp())
                                    .atOffset(ZoneOffset.UTC)
                    );
                    event.setApplicationId(new String(
                            instanceFlowConsumerRecord.getConsumerRecord()
                                    .headers()
                                    .lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER)
                                    .value(),
                            StandardCharsets.UTF_8
                    ));
                    eventRepository.save(event);
                },
                new CommonLoggingErrorHandler(),
                false
        ).createContainer(createEventTopicNameParameters(eventName));
    }

    private EventTopicNameParameters createEventTopicNameParameters(String eventName) {
        return EventTopicNameParameters.builder()
                .eventName(eventName)
                .build();
    }

}
