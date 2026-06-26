package no.novari.flyt.history.kafka

import no.novari.flyt.history.ErrorScrubService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowErrorHandlerConfiguration
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowErrorHandlerFactory
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

@Configuration
class InstanceDeletedConsumerConfiguration(
    private val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService,
    private val instanceFlowErrorHandlerFactory: InstanceFlowErrorHandlerFactory,
) {
    @Bean
    fun instanceDeletedConsumer(
        errorScrubService: ErrorScrubService,
    ): ConcurrentMessageListenerContainer<String, Any> {
        return instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                Any::class.java,
                { instanceFlowConsumerRecord ->
                    errorScrubService.scrubByInstanceFlowHeaders(instanceFlowConsumerRecord.instanceFlowHeaders)
                },
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefaultWithSuffix("instance-deleted-scrubber")
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .seekToBeginningOnAssignment()
                    .build(),
                instanceFlowErrorHandlerFactory.createErrorHandler(
                    InstanceFlowErrorHandlerConfiguration
                        .stepBuilder<Any>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(
                EventTopicNameParameters
                    .builder()
                    .eventName(EventCategory.INSTANCE_DELETED.eventName)
                    .topicNamePrefixParameters(topicNamePrefixParameters())
                    .build(),
            )
    }

    private fun topicNamePrefixParameters(): TopicNamePrefixParameters {
        return TopicNamePrefixParameters
            .stepBuilder()
            .orgIdApplicationDefault()
            .domainContextApplicationDefault()
            .build()
    }
}
