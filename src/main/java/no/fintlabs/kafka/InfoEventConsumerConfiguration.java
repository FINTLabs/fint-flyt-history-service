package no.fintlabs.kafka;

import no.fintlabs.EventRepository;
import no.fintlabs.flyt.kafka.event.InstanceFlowEventConsumerFactoryService;
import no.fintlabs.kafka.event.topic.EventTopicNameParameters;
import no.fintlabs.mapping.InstanceFlowHeadersMappingService;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.eventinfo.EventInfo;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
public class InfoEventConsumerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowEventConsumerFactoryService instanceFlowEventConsumerFactoryService;
    private final InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;


    public InfoEventConsumerConfiguration(
            EventRepository eventRepository,
            InstanceFlowEventConsumerFactoryService instanceFlowEventConsumerFactoryService,
            InstanceFlowHeadersMappingService skjemaEventHeadersMapper) {
        this.eventRepository = eventRepository;
        this.instanceFlowEventConsumerFactoryService = instanceFlowEventConsumerFactoryService;
        this.instanceFlowHeadersMappingService = skjemaEventHeadersMapper;
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceReceivedEventConsumer() {
        return createInfoEventListener(InstanceStatusEvent.INSTANCE_RECEIVED);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceRegisteredEventConsumer() {
        return createInfoEventListener(InstanceStorageStatusEvent.INSTANCE_REGISTERED);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceRequestedForRetry() {
        return createInfoEventListener(InstanceStatusEvent.INSTANCE_REQUESTED_FOR_RETRY);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceMappedEventConsumer() {
        return createInfoEventListener(InstanceStatusEvent.INSTANCE_MAPPED);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceReadyForDispatchEventConsumer() {
        return createInfoEventListener(InstanceStatusEvent.INSTANCE_READY_FOR_DISPATCH);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceDispatchedEventConsumer() {
        return createInfoEventListener(InstanceStatusEvent.INSTANCE_DISPATCHED);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> instanceDeletedEventConsumer() {
        return createInfoEventListener(InstanceStorageStatusEvent.INSTANCE_DELETED);
    }

    private ConcurrentMessageListenerContainer<String, Object> createInfoEventListener(EventInfo eventInfo) {
        return instanceFlowEventConsumerFactoryService.createRecordFactory(
                Object.class,
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
                    eventEntity.setApplicationId(new String(
                            instanceFlowConsumerRecord.getConsumerRecord()
                                    .headers()
                                    .lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER)
                                    .value(),
                            StandardCharsets.UTF_8
                    ));
                    eventRepository.save(eventEntity);
                }
        ).createContainer(createEventTopicNameParameters(eventInfo.getName()));
    }

    private EventTopicNameParameters createEventTopicNameParameters(String eventName) {
        return EventTopicNameParameters.builder()
                .eventName(eventName)
                .build();
    }

}
