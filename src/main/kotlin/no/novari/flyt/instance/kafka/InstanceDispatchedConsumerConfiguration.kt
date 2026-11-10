package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceDispatchedConsumerConfiguration {
    @Bean
    fun instanceDispatchedConsumer(
        listenerFactory: InstanceFlowListenerFactoryService,
        deletionService: InstanceDeletionService,
        errorHandlerFactory: ErrorHandlerFactory,
    ): ConcurrentMessageListenerContainer<String, InstanceObjectDto> =
        listenerFactory
            .createRecordListenerContainerFactory(
                InstanceObjectDto::class.java,
                { record -> deletionService.delete(record.instanceFlowHeaders) },
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefaultWithSuffix("-instance-dispatched")
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .continueFromPreviousOffsetOnAssignment()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<InstanceObjectDto>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(eventTopicNameParameters(EventCategory.INSTANCE_DISPATCHED))
}
