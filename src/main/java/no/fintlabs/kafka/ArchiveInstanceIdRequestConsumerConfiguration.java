package no.fintlabs.kafka;

import no.fintlabs.EventService;
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class ArchiveInstanceIdRequestConsumerConfiguration {

    @Bean
    ConcurrentMessageListenerContainer<String, ArchiveInstanceIdRequestParams> archiveInstanceIdRequestConsumer(
            RequestTopicService requestTopicService,
            RequestConsumerFactoryService requestConsumerFactoryService,
            EventService eventService) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resource("archive.instance.id")
                .parameterName("source-application-instance-id")
                .build();

        requestTopicService.ensureTopic(topicNameParameters, 345600000, TopicCleanupPolicyParameters.builder().build());

        // TODO 25/02/2025 eivindmorch: ACOS cannot send sourceApplicationIntegrationId, and it is not necessary.
        //  Make it an optional parameter, and revert to old topic name?
        return requestConsumerFactoryService.createRecordConsumerFactory(
                ArchiveInstanceIdRequestParams.class,
                String.class,
                consumerRecord -> {
                    String archiveInstanceId = eventService.findLatestArchiveInstanceId(consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<String>builder().value(archiveInstanceId).build();
                }
        ).createContainer(topicNameParameters);
    }

}
