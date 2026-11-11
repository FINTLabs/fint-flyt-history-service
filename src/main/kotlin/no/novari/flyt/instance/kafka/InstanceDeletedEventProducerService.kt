package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
@ConditionalOnInstanceFunctionality
class InstanceDeletedEventProducerService(
    templateFactory: InstanceFlowTemplateFactory,
    topicService: EventTopicService,
    @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    retentionTime: Duration,
) {
    private val template = templateFactory.createTemplate(InstanceObjectDto::class.java)
    private val topic = eventTopicNameParameters(EventCategory.INSTANCE_DELETED)

    init {
        topicService.createOrModifyTopic(
            topic,
            EventTopicConfiguration
                .stepBuilder()
                .partitions(1)
                .retentionTime(retentionTime)
                .cleanupFrequency(EventCleanupFrequency.NORMAL)
                .build(),
        )
    }

    fun publish(headers: InstanceFlowHeaders) {
        template.send(
            InstanceFlowProducerRecord
                .builder<InstanceObjectDto>()
                .instanceFlowHeaders(headers)
                .topicNameParameters(topic)
                .value(null)
                .build(),
        )
    }
}
