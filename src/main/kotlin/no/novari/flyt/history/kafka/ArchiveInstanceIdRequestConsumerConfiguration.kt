package no.novari.flyt.history.kafka

import no.novari.flyt.history.EventService
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.requestreply.ReplyProducerRecord
import no.novari.kafka.requestreply.RequestListenerConfiguration
import no.novari.kafka.requestreply.RequestListenerContainerFactory
import no.novari.kafka.requestreply.topic.RequestTopicService
import no.novari.kafka.requestreply.topic.configuration.RequestTopicConfiguration
import no.novari.kafka.requestreply.topic.name.RequestTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.time.Duration

@Configuration
class ArchiveInstanceIdRequestConsumerConfiguration(
    private val errorHandlerFactory: ErrorHandlerFactory,
) {
    @Bean
    fun archiveInstanceIdRequestConsumer(
        requestTopicService: RequestTopicService,
        requestListenerContainerFactory: RequestListenerContainerFactory,
        eventService: EventService,
    ): ConcurrentMessageListenerContainer<String, ArchiveInstanceIdRequestParams> {
        val topicNameParameters =
            RequestTopicNameParameters
                .builder()
                .resourceName("archive-instance-id")
                .topicNamePrefixParameters(
                    TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build(),
                ).parameterName("source-application-instance-id")
                .build()

        requestTopicService.createOrModifyTopic(
            topicNameParameters,
            RequestTopicConfiguration
                .builder()
                .retentionTime(RETENTION_DURATION)
                .build(),
        )

        return requestListenerContainerFactory
            .createRecordConsumerFactory(
                ArchiveInstanceIdRequestParams::class.java,
                String::class.java,
                { consumerRecord ->
                    ReplyProducerRecord
                        .builder<String>()
                        .value(eventService.findLatestArchiveInstanceId(consumerRecord.value()))
                        .build()
                },
                RequestListenerConfiguration
                    .stepBuilder(ArchiveInstanceIdRequestParams::class.java)
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<ArchiveInstanceIdRequestParams>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(topicNameParameters)
    }

    companion object {
        private val RETENTION_DURATION: Duration = Duration.ofMinutes(10)
    }
}
