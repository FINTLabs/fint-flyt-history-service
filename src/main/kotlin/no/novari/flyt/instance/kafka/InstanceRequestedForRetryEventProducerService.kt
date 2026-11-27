package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
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
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.jvm.optionals.getOrDefault

@Configuration
@ConditionalOnInstanceFunctionality
class InstanceRequestedForRetryTopicConfiguration {
    @Bean
    fun instanceRequestedForRetryTopic(topics: KafkaTopics): EventTopicName =
        topics.event(EventCategory.INSTANCE_REQUESTED_FOR_RETRY.eventName)

    @Bean
    fun instanceRequestedForRetryTopicCreation(
        topics: KafkaTopics,
        instanceRequestedForRetryTopic: EventTopicName,
        @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
        retentionTime: Duration,
    ): NewTopic = topics.newTopic(instanceRequestedForRetryTopic, eventTopicConfig(retentionTime))
}

@Service
@ConditionalOnInstanceFunctionality
class InstanceRequestedForRetryEventProducerService(
    private val kafkaTemplate: KafkaTemplate<String, InstanceObjectDto>,
    // By name: with one EventTopicName bean in a context, Spring would inject it whatever its name.
    @param:Qualifier("instanceRequestedForRetryTopic")
    private val instanceRequestedForRetryTopic: EventTopicName,
    private val auditorAware: AuditorAware<Actor>,
) {
    fun publish(
        headers: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ) {
        kafkaTemplate.send(
            instanceRequestedForRetryTopic,
            headers,
            instance,
            additionalHeaders =
                mapOf(
                    ActorHeader.HEADER_NAME to
                        ActorHeader.toHeaderValue(auditorAware.currentAuditor.getOrDefault(Actor.System)),
                ),
        )
    }
}
