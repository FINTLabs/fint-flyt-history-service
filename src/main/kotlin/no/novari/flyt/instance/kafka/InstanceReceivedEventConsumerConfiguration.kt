package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceReceivedPositions
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.consuming.ParameterizedListenerContainerFactoryService
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceReceivedEventConsumerConfiguration(
    eventTopicService: EventTopicService,
    @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    retentionTime: Duration,
) {
    init {
        eventTopicService.createOrModifyTopic(
            eventTopicNameParameters(EventCategory.INSTANCE_RECEIVED),
            EventTopicConfiguration
                .stepBuilder()
                .partitions(1)
                .retentionTime(retentionTime)
                .cleanupFrequency(EventCleanupFrequency.NORMAL)
                .build(),
        )
    }

    // Plain records, not InstanceFlowListenerFactoryService: unusable headers must be handled, not retried forever.
    @Bean
    fun instanceReceivedEventConsumer(
        listenerContainerFactoryService: ParameterizedListenerContainerFactoryService,
        errorHandlerFactory: ErrorHandlerFactory,
        registrationService: InstanceRegistrationService,
        positions: InstanceReceivedPositions,
    ): ConcurrentMessageListenerContainer<String, InstanceObjectDto> =
        listenerContainerFactoryService
            .createRecordListenerContainerFactory(
                InstanceObjectDto::class.java,
                registrationService::register,
                ListenerConfiguration
                    .stepBuilder()
                    // Own group: the INSTANCE_RECEIVED history listener reads the same topic with the default group.
                    .groupIdApplicationDefaultWithSuffix("-instance-received")
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    // The database position is the source of truth, so committed Kafka offsets never decide the start.
                    .onAssignment { assignments, callback -> positions.assigned(assignments.keys, callback) }
                    .onRevocation(positions::revoked)
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<InstanceObjectDto>()
                        .retryWithFixedInterval(Duration.ofSeconds(10), 3)
                        // Includes exceptions Spring treats as fatal, so they also back off between attempts.
                        .retryOnly(listOf(Exception::class.java))
                        .restartRetryOnExceptionChange()
                        // Never skip a record: the partition waits until the cause is fixed.
                        .recoverFailedRecords { _, exception -> throw exception }
                        .reprocessAndRetryRecordOnRecoveryFailure()
                        .build(),
                ),
            ).createContainer(eventTopicNameParameters(EventCategory.INSTANCE_RECEIVED))
}
