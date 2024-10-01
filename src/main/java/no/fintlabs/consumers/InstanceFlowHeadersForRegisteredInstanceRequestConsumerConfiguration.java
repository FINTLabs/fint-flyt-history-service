package no.fintlabs.consumers;

import no.fintlabs.InstanceFlowHeadersEmbeddableMapper;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import no.fintlabs.model.Event;
import no.fintlabs.repositories.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import static no.fintlabs.EventNames.INSTANCE_REGISTERED;

@Configuration
public class InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration {

    private final EventRepository eventRepository;
    private final InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper;

    public InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration(
            EventRepository eventRepository,
            InstanceFlowHeadersEmbeddableMapper instanceFlowHeadersEmbeddableMapper
    ) {
        this.eventRepository = eventRepository;
        this.instanceFlowHeadersEmbeddableMapper = instanceFlowHeadersEmbeddableMapper;
    }

    @Bean
    ConcurrentMessageListenerContainer<String, Long> instanceFlowHeadersForRegisteredInstanceRequestConsumer(
            RequestTopicService requestTopicService,
            RequestConsumerFactoryService requestConsumerFactoryService
    ) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resource("instance-flow-headers-for-registered-instance")
                .parameterName("instance-id")
                .build();

        requestTopicService.ensureTopic(topicNameParameters, 0, TopicCleanupPolicyParameters.builder().build());

        return requestConsumerFactoryService.createRecordConsumerFactory(
                Long.class,
                InstanceFlowHeaders.class,
                consumerRecord -> {
                    InstanceFlowHeaders instanceFlowHeaders = eventRepository
                            .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                                    consumerRecord.value(),
                                    INSTANCE_REGISTERED
                            )
                            .map(Event::getInstanceFlowHeaders)
                            .map(instanceFlowHeadersEmbeddableMapper::toInstanceFlowHeaders)
                            .orElse(null);
                    return ReplyProducerRecord.<InstanceFlowHeaders>builder().value(instanceFlowHeaders).build();
                }
        ).createContainer(topicNameParameters);
    }

}
