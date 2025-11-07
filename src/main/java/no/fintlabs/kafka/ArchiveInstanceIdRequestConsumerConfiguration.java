package no.fintlabs.kafka;

import lombok.AllArgsConstructor;
import no.fintlabs.EventService;
import no.fintlabs.kafka.consuming.ErrorHandlerConfiguration;
import no.fintlabs.kafka.consuming.ErrorHandlerFactory;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestListenerConfiguration;
import no.fintlabs.kafka.requestreply.RequestListenerContainerFactory;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import no.fintlabs.kafka.requestreply.topic.configuration.RequestTopicConfiguration;
import no.fintlabs.kafka.requestreply.topic.name.RequestTopicNameParameters;
import no.fintlabs.kafka.topic.name.TopicNamePrefixParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.time.Duration;

@Configuration
@AllArgsConstructor
public class ArchiveInstanceIdRequestConsumerConfiguration {

    private static final Duration RETENTION_DURATION = Duration.ofMinutes(5);
    private final ErrorHandlerFactory errorHandlerFactory;

    @Bean
    ConcurrentMessageListenerContainer<String, ArchiveInstanceIdRequestParams> archiveInstanceIdRequestConsumer(
            RequestTopicService requestTopicService,
            RequestListenerContainerFactory requestListenerContainerFactory,
            EventService eventService) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resourceName("archive-instance-id")
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters
                                .builder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .parameterName("source-application-instance-id")
                .build();

        requestTopicService.createOrModifyTopic(topicNameParameters, RequestTopicConfiguration.builder().retentionTime(RETENTION_DURATION).build());

        return requestListenerContainerFactory.createRecordConsumerFactory(
                ArchiveInstanceIdRequestParams.class,
                String.class,
                consumerRecord -> {
                    String archiveInstanceId = eventService.findLatestArchiveInstanceId(consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<String>builder().value(archiveInstanceId).build();
                },
                RequestListenerConfiguration
                        .stepBuilder(ArchiveInstanceIdRequestParams.class)
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
