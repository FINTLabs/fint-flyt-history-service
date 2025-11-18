package no.novari.flyt.history.kafka;

import lombok.AllArgsConstructor;
import no.novari.flyt.history.EventService;
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders;
import no.novari.kafka.consuming.ErrorHandlerConfiguration;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.requestreply.ReplyProducerRecord;
import no.novari.kafka.requestreply.RequestListenerConfiguration;
import no.novari.kafka.requestreply.RequestListenerContainerFactory;
import no.novari.kafka.requestreply.topic.RequestTopicService;
import no.novari.kafka.requestreply.topic.configuration.RequestTopicConfiguration;
import no.novari.kafka.requestreply.topic.name.RequestTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.time.Duration;

@Configuration
@AllArgsConstructor
public class InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration {

    private final EventService eventService;
    private static final Duration RETENTION_DURATION = Duration.ofMinutes(5);
    private final ErrorHandlerFactory errorHandlerFactory;

    @Bean
    ConcurrentMessageListenerContainer<String, Long> instanceFlowHeadersForRegisteredInstanceRequestConsumer(
            RequestTopicService requestTopicService,
            RequestListenerContainerFactory requestListenerContainerFactory
    ) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resourceName("instance-flow-headers-for-registered-instance")
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters
                                .stepBuilder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .parameterName("instance-id")
                .build();

        requestTopicService.createOrModifyTopic(topicNameParameters, RequestTopicConfiguration.builder().retentionTime(RETENTION_DURATION).build());

        return requestListenerContainerFactory.createRecordConsumerFactory(
                Long.class,
                InstanceFlowHeaders.class,
                consumerRecord -> {
                    InstanceFlowHeaders instanceFlowHeaders = eventService
                            .findInstanceFlowHeadersForLatestInstanceRegisteredEvent(consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<InstanceFlowHeaders>builder().value(instanceFlowHeaders).build();
                },
                RequestListenerConfiguration
                        .stepBuilder(Long.class)
                        .maxPollRecordsKafkaDefault()
                        .maxPollIntervalKafkaDefault()
                        .build(),
                errorHandlerFactory.createErrorHandler(
                        ErrorHandlerConfiguration
                                .stepBuilder()
                                .noRetries()
                                .skipFailedRecords()
                                .build()
                )
        ).createContainer(topicNameParameters);
    }

}
