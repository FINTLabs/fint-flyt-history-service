package no.novari.flyt.instance.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceReceivedEventConsumerConfiguration {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun instanceReceivedEventConsumer(
        instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService,
        errorHandlerFactory: ErrorHandlerFactory,
        registrationService: InstanceRegistrationService,
        offsetRepository: InstanceReceivedOffsetRepository,
    ): ConcurrentMessageListenerContainer<String, InstanceObjectDto> =
        instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                InstanceObjectDto::class.java,
                { record ->
                    with(record.consumerRecord) {
                        registrationService.register(
                            topic(),
                            partition(),
                            offset(),
                            record.instanceFlowHeaders,
                            requireNotNull(value()) { "Missing instance payload" },
                        )
                    }
                },
                ListenerConfiguration
                    .stepBuilder()
                    // Own group: the INSTANCE_RECEIVED history listener reads the same topic with the default group.
                    .groupIdApplicationDefaultWithSuffix("-instance-received")
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    // The database position is the source of truth, so committed Kafka offsets never decide the start.
                    .onAssignment { assignments, callback ->
                        assignments.keys.forEach { partition ->
                            val nextOffset = offsetRepository.findNextOffset(partition.topic(), partition.partition())
                            if (nextOffset == null) {
                                logger.error {
                                    "No instance_received_offset row for $partition; processing waits for it"
                                }
                            } else {
                                callback.seek(partition.topic(), partition.partition(), nextOffset)
                            }
                        }
                    }.build(),
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
