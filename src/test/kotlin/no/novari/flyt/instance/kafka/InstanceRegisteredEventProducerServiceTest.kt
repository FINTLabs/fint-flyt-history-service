package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxProperties
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.DescribeConfigsResult
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.TopicConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class InstanceRegisteredEventProducerServiceTest {
    private val template: KafkaTemplate<String, InstanceObjectDto> = mock()
    private val admin: Admin = mock()
    private val topics: EventTopicService = mock()
    private val topicNames: TopicNameService = mock()
    private val headersMapper = InstanceFlowHeadersMapper(outboxObjectMapper())
    private val event = outboxEvent()
    private lateinit var producer: InstanceRegisteredEventProducerService

    @BeforeEach
    fun setUp() {
        whenever(topicNames.validateAndMapToTopicName(any())).thenReturn("instance-registered-test")
        timestampType("CreateTime")
        producer =
            InstanceRegisteredEventProducerService(
                template,
                admin,
                headersMapper,
                topicNames,
                topics,
                InstanceRegisteredOutboxProperties(sendTimeout = Duration.ofMillis(100)),
                Duration.ofDays(4),
            )
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>())).thenAnswer {
            val record = it.getArgument<ProducerRecord<String, InstanceObjectDto>>(0)
            CompletableFuture.completedFuture(
                SendResult(record, RecordMetadata(TopicPartition(record.topic(), 0), 0, 0, record.timestamp(), 0, 0)),
            )
        }
    }

    @Test
    fun `preserves the registered topic configuration`() {
        val names = argumentCaptor<EventTopicNameParameters>()
        val configuration = argumentCaptor<EventTopicConfiguration>()
        verify(topics).createOrModifyTopic(names.capture(), configuration.capture())
        assertThat(names.firstValue.eventName).isEqualTo("instance-registered")
        assertThat(configuration.firstValue.partitions).isEqualTo(1)
        assertThat(configuration.firstValue.retentionTime).isEqualTo(Duration.ofDays(4))
    }

    @Test
    fun `publishes stored timestamp headers origin and DTO after acknowledgement`() {
        producer.publish(event)

        val record = argumentCaptor<ProducerRecord<String, InstanceObjectDto>>()
        verify(template).send(record.capture())
        assertThat(record.firstValue.topic()).isEqualTo("instance-registered-test")
        assertThat(record.firstValue.timestamp()).isEqualTo(event.timestamp.toEpochMilli())
        assertThat(record.firstValue.key()).isNull()
        assertThat(record.firstValue.value()).isSameAs(event.instance)
        assertThat(headersMapper.getInstanceFlowHeaders(record.firstValue.headers())).isEqualTo(event.headers)
        assertThat(
            String(
                record.firstValue
                    .headers()
                    .lastHeader(
                        OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                    ).value(),
            ),
        ).isEqualTo(event.applicationId)
    }

    @Test
    fun `a failed send future fails publication`() {
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>()))
            .thenReturn(CompletableFuture.failedFuture(IllegalStateException("broker rejected record")))
        assertThatThrownBy { producer.publish(event) }.isInstanceOf(ExecutionException::class.java)
    }

    @Test
    fun `an unacknowledged send times out`() {
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>())).thenReturn(CompletableFuture())
        assertThatThrownBy { producer.publish(event) }.isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `LogAppendTime is rejected before sending`() {
        timestampType("LogAppendTime")
        assertThatThrownBy { producer.publish(event) }.hasMessage("instance-registered must use CreateTime")
        verifyNoInteractions(template)
    }

    private fun timestampType(value: String) {
        val result: DescribeConfigsResult = mock()
        val resource = ConfigResource(ConfigResource.Type.TOPIC, "instance-registered-test")
        whenever(admin.describeConfigs(any<Collection<ConfigResource>>())).thenReturn(result)
        whenever(result.all()).thenReturn(
            KafkaFuture.completedFuture(
                mapOf(resource to Config(listOf(ConfigEntry(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG, value)))),
            ),
        )
    }
}
