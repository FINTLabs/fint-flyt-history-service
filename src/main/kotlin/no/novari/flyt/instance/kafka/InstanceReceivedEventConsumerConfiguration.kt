package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicConfig
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceReceivedPositions
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.kafka.starter.consumer.KafkaGroupIds
import no.novari.kafka.starter.topic.EventTopicName
import no.novari.kafka.starter.topic.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListener
import org.springframework.util.backoff.FixedBackOff
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceReceivedEventConsumerConfiguration {
    @Bean
    fun instanceReceivedTopic(topics: KafkaTopics): EventTopicName =
        topics.event(EventCategory.INSTANCE_RECEIVED.eventName)

    @Bean
    fun instanceReceivedTopicCreation(
        topics: KafkaTopics,
        instanceReceivedTopic: EventTopicName,
        @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
        retentionTime: Duration,
    ): NewTopic = topics.newTopic(instanceReceivedTopic, eventTopicConfig(retentionTime))

    // A container of its own, not @KafkaListener: it seeks to the database position and must never skip a record.
    @Bean
    fun instanceReceivedEventConsumer(
        factory: ConcurrentKafkaListenerContainerFactory<String, String>,
        instanceReceivedTopic: EventTopicName,
        kafkaGroupIds: KafkaGroupIds,
        registrationService: InstanceRegistrationService,
        positions: InstanceReceivedPositions,
    ): ConcurrentMessageListenerContainer<String, String> =
        factory.createContainer(instanceReceivedTopic.name).apply {
            // Own group: the INSTANCE_RECEIVED history listener reads the same topic with the default group.
            containerProperties.setGroupId(kafkaGroupIds.withSuffix("-instance-received"))
            containerProperties.setMessageListener(InstanceReceivedListener(registrationService, positions))
            commonErrorHandler =
                // Never skip a record: the recoverer rethrows, so the partition waits until the cause is fixed.
                DefaultErrorHandler(
                    { _, exception -> throw exception },
                    FixedBackOff(RETRY_INTERVAL_MS, RETRIES),
                ).apply {
                    // Includes exceptions Spring treats as fatal, so they also back off between attempts.
                    defaultFalse()
                    addRetryableExceptions(Exception::class.java)
                }
        }

    private class InstanceReceivedListener(
        private val registrationService: InstanceRegistrationService,
        private val positions: InstanceReceivedPositions,
    ) : MessageListener<String, String>,
        ConsumerSeekAware {
        override fun onMessage(record: ConsumerRecord<String, String>) = registrationService.register(record)

        // The database position is the source of truth, so committed Kafka offsets never decide the start.
        override fun onPartitionsAssigned(
            assignments: Map<TopicPartition, Long>,
            callback: ConsumerSeekAware.ConsumerSeekCallback,
        ) {
            positions.assigned(assignments.keys, callback)
        }

        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>?) {
            positions.revoked(partitions ?: emptyList())
        }
    }

    private companion object {
        const val RETRY_INTERVAL_MS = 10_000L
        const val RETRIES = 3L
    }
}
