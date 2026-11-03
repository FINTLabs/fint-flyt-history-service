package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.AuditorAware
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnInstanceFunctionality
class InstanceRequestedForRetryEventProducerService(
    instanceFlowTemplateFactory: InstanceFlowTemplateFactory,
    eventTopicService: EventTopicService,
    private val auditorAware: AuditorAware<Actor>,
    private val properties: InstanceRetryProperties,
    @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    retentionTime: Duration,
) {
    private val template = instanceFlowTemplateFactory.createTemplate(InstanceObjectDto::class.java)
    private val topicNameParameters =
        EventTopicNameParameters
            .builder()
            .topicNamePrefixParameters(
                TopicNamePrefixParameters
                    .stepBuilder()
                    .orgIdApplicationDefault()
                    .domainContextApplicationDefault()
                    .build(),
            ).eventName("instance-requested-for-retry")
            .build()

    init {
        eventTopicService.createOrModifyTopic(
            topicNameParameters,
            EventTopicConfiguration
                .stepBuilder()
                .partitions(1)
                .retentionTime(retentionTime)
                .cleanupFrequency(EventCleanupFrequency.NORMAL)
                .build(),
        )
    }

    fun publish(
        headers: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ) {
        val record =
            InstanceFlowProducerRecord
                .builder<InstanceObjectDto>()
                .instanceFlowHeaders(headers)
                .topicNameParameters(topicNameParameters)
                .additionalHeader(
                    ActorHeader.HEADER_NAME,
                    ActorHeader.toHeaderValue(auditorAware.currentAuditor.orElse(Actor.System)),
                ).value(instance)
                .build()
        try {
            template.send(record).get(properties.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        }
    }
}
