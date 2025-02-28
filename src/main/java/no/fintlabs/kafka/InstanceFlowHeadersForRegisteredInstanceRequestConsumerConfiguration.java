package no.fintlabs.kafka;

import no.fintlabs.EventService;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

// TODO 20/12/2024 eivindmorch: Test
@Configuration
public class InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration {

    private final EventService eventService;

    public InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration(EventService eventService) {
        this.eventService = eventService;
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
                    InstanceFlowHeaders instanceFlowHeaders = eventService
                            .findInstanceFlowHeadersForLatestInstanceRegisteredEvent(consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<InstanceFlowHeaders>builder().value(instanceFlowHeaders).build();
                }
        ).createContainer(topicNameParameters);
    }

}
