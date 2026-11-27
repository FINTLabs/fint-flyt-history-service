package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicConfig
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.send
import no.novari.kafka.starter.topic.EventTopicName
import no.novari.kafka.starter.topic.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Configuration
@ConditionalOnInstanceFunctionality
class InstanceRegisteredTopicConfiguration {
    @Bean
    fun instanceRegisteredTopic(topics: KafkaTopics): EventTopicName =
        topics.event(EventCategory.INSTANCE_REGISTERED.eventName)

    @Bean
    fun instanceRegisteredTopicCreation(
        topics: KafkaTopics,
        instanceRegisteredTopic: EventTopicName,
        @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
        retentionTime: Duration,
    ): NewTopic = topics.newTopic(instanceRegisteredTopic, eventTopicConfig(retentionTime))
}

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegisteredEventProducerService(
    private val kafkaTemplate: KafkaTemplate<String, InstanceObjectDto>,
    // By name: with one EventTopicName bean in a context, Spring would inject it whatever its name.
    @param:Qualifier("instanceRegisteredTopic")
    private val instanceRegisteredTopic: EventTopicName,
) {
    // Waits for the broker acknowledgement, so the caller only moves the position after a successful send.
    fun publish(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ) {
        kafkaTemplate.send(instanceRegisteredTopic, instanceFlowHeaders, instance).get()
    }
}
