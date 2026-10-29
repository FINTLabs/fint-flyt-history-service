package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceReceivedEventConsumerConfiguration {
    @Bean(defaultCandidate = false)
    fun instanceReceivedConsumerFactory(
        consumerConfig: ConsumerConfig,
    ): DefaultKafkaConsumerFactory<String, ByteArray> {
        val configuration = consumerConfig.originals().toMutableMap()
        configuration[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configuration[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        configuration[ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG] = false
        return DefaultKafkaConsumerFactory(configuration, StringDeserializer(), ByteArrayDeserializer())
    }

    @Bean
    fun instanceReceivedEventConsumer(
        @Qualifier("instanceReceivedConsumerFactory") factory: DefaultKafkaConsumerFactory<String, ByteArray>,
        topicNameService: TopicNameService,
        listener: InstanceReceivedEventListener,
        properties: InstanceReceivedKafkaProperties,
    ): ConcurrentMessageListenerContainer<String, ByteArray> {
        val topic =
            topicNameService.validateAndMapToTopicName(
                eventTopicNameParameters(EventCategory.INSTANCE_RECEIVED),
            )
        val containerProperties =
            ContainerProperties(topic).apply {
                messageListener = listener
                consumerRebalanceListener = listener
                ackMode = ContainerProperties.AckMode.RECORD
                assignmentCommitOption = ContainerProperties.AssignmentCommitOption.NEVER
                isStopImmediate = true
                isMissingTopicsFatal = true
            }
        return ConcurrentMessageListenerContainer(factory, containerProperties).apply {
            beanName = "instanceReceivedEventConsumer"
            setCommonErrorHandler(InstanceReceivedErrorHandler(this, properties))
        }
    }
}
