package no.novari.flyt.history.kafka

import no.novari.flyt.history.EventService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
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
class InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration(
    private val eventService: EventService,
    private val errorHandlerFactory: ErrorHandlerFactory,
) {
    @Bean
    fun instanceFlowHeadersForRegisteredInstanceRequestConsumer(
        requestTopicService: RequestTopicService,
        requestListenerContainerFactory: RequestListenerContainerFactory,
    ): ConcurrentMessageListenerContainer<String, Long> {
        val topicNameParameters =
            RequestTopicNameParameters
                .builder()
                .resourceName("instance-flow-headers-for-registered-instance")
                .topicNamePrefixParameters(
                    TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build(),
                ).parameterName("instance-id")
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
                Long::class.java,
                InstanceFlowHeaders::class.java,
                { consumerRecord ->
                    ReplyProducerRecord
                        .builder<InstanceFlowHeaders>()
                        .value(
                            eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(
                                consumerRecord.value(),
                            ),
                        ).build()
                },
                RequestListenerConfiguration
                    .stepBuilder(Long::class.java)
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<Long>()
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
