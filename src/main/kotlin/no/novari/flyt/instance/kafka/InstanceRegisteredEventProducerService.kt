package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxEvent
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxProperties
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegisteredEventProducerService(
    @param:Qualifier("instanceRegisteredOutboxKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, InstanceObjectDto>,
    @Qualifier("instanceRegisteredOutboxAdminSupplier")
    adminSupplier: Supplier<Admin>,
    private val headersMapper: InstanceFlowHeadersMapper,
    private val topicNameService: TopicNameService,
    eventTopicService: EventTopicService,
    private val properties: InstanceRegisteredOutboxProperties,
    @param:Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    private val retentionTime: Duration,
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
            ).eventName("instance-registered")
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
        adminSupplier.get().use { admin ->
            val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
            val configuration =
                admin
                    .describeConfigs(listOf(resource))
                    .all()
                    .get(properties.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
            val timestampType = configuration.getValue(resource).get(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG)?.value()
            check(timestampType == "CreateTime") {
                "instance-registered must use CreateTime"
            }
        }
    }

    fun publish(event: InstanceRegisteredOutboxEvent) {
        val headers =
            RecordHeaders().add(headersMapper.toHeader(event.headers)).add(
                OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                event.applicationId.toByteArray(Charsets.UTF_8),
            )
        val record =
            ProducerRecord<String, InstanceObjectDto>(
                topicName,
                null,
                event.timestamp.toEpochMilli(),
                null,
                event.instance,
                headers,
            )
        kafkaTemplate.send(record).get(properties.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
    }
}
