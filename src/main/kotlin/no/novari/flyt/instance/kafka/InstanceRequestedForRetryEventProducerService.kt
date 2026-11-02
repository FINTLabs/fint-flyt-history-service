package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnInstanceFunctionality
class InstanceRequestedForRetryEventProducerService(
    @param:Qualifier("instanceRetryKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, InstanceObjectDto>,
    private val headersMapper: InstanceFlowHeadersMapper,
    topicNameService: TopicNameService,
    eventTopicService: EventTopicService,
    private val auditorAware: AuditorAware<Actor>,
    private val properties: InstanceRetryProperties,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
    @Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    retentionTime: Duration,
) {
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
    private val topicName = topicNameService.validateAndMapToTopicName(topicNameParameters)

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
        val recordHeaders =
            RecordHeaders()
                .add(headersMapper.toHeader(headers))
                .add(
                    ActorHeader.HEADER_NAME,
                    ActorHeader.toHeaderValue(auditorAware.currentAuditor.orElse(Actor.System)),
                ).add(
                    OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                    applicationId.toByteArray(Charsets.UTF_8),
                )
        val record = ProducerRecord<String, InstanceObjectDto>(topicName, null, null, null, instance, recordHeaders)
        try {
            kafkaTemplate.send(record).get(properties.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        }
    }
}
