package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxKafkaConfiguration
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxProperties
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.serializer.JsonSerializer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
class InstanceRegisteredEventKafkaIntegrationTest {
    @Test
    fun `broker record preserves snapshot metadata despite a different runtime origin interceptor`() {
        publishToBroker("CreateTime")
    }

    @Test
    fun `LogAppendTime topic retains the pending publication instead of sending incorrect metadata`() {
        publishToBroker("LogAppendTime")
    }

    private fun publishToBroker(timestampType: String) {
        val mapper = outboxObjectMapper()
        val headersMapper = InstanceFlowHeadersMapper(mapper)
        val properties = InstanceRegisteredOutboxProperties()
        val interceptorClass = OriginHeaderProducerInterceptor::class.java.name
        val sharedConfig =
            ProducerConfig(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                    ProducerConfig.INTERCEPTOR_CLASSES_CONFIG to listOf(interceptorClass),
                    OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_PRODUCER_CONFIG to "different-runtime-app",
                ),
            )
        val configuration = InstanceRegisteredOutboxKafkaConfiguration()
        val factory = configuration.instanceRegisteredOutboxProducerFactory(sharedConfig, mapper, properties)
        val template = configuration.instanceRegisteredOutboxKafkaTemplate(factory)
        try {
            configuration.instanceRegisteredOutboxAdmin(sharedConfig, properties).use { admin ->
                val topic = "instance-registered-outbox-${UUID.randomUUID()}"
                admin
                    .createTopics(
                        listOf(
                            NewTopic(topic, 1, 1).configs(
                                mapOf(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG to timestampType),
                            ),
                        ),
                    ).all()
                    .get()
                val names: TopicNameService = mock()
                whenever(names.validateAndMapToTopicName(any())).thenReturn(topic)
                val producer =
                    InstanceRegisteredEventProducerService(
                        template,
                        admin,
                        headersMapper,
                        names,
                        mock<EventTopicService>(),
                        properties,
                        Duration.ofDays(4),
                    )
                val event =
                    outboxEvent().copy(
                        timestamp = Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.MILLIS),
                    )
                KafkaConsumer<ByteArray, ByteArray>(
                    mapOf(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                    ),
                ).use { consumer ->
                    val partition = TopicPartition(topic, 0)
                    consumer.assign(listOf(partition))
                    consumer.seekToBeginning(listOf(partition))
                    if (timestampType == "LogAppendTime") {
                        assertThatThrownBy {
                            producer.publish(
                                event,
                            )
                        }.hasMessage("instance-registered must use CreateTime")
                        assertThat(consumer.endOffsets(listOf(partition)).getValue(partition)).isZero()
                    } else {
                        producer.publish(event)
                        val record = consumer.poll(Duration.ofSeconds(15)).single()
                        assertThat(record.timestamp()).isEqualTo(event.timestamp.toEpochMilli())
                        assertThat(record.key()).isNull()
                        val actualPayload = mapper.readTree(record.value())
                        val expectedPayload = mapper.readTree(mapper.writeValueAsBytes(event.instance))
                        assertThat(actualPayload).isEqualTo(expectedPayload)
                        assertThat(mapper.readTree(record.value()).get("id").asLong()).isEqualTo(123L)
                        assertThat(headersMapper.getInstanceFlowHeaders(record.headers())).isEqualTo(event.headers)
                        val origins =
                            record
                                .headers()
                                .headers(
                                    OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                                ).toList()
                        assertThat(origins).hasSize(1)
                        assertThat(String(origins.single().value(), Charsets.UTF_8)).isEqualTo(event.applicationId)
                        assertThat(factory.configurationProperties[ProducerConfig.ACKS_CONFIG]).isEqualTo("all")
                        assertThat(
                            factory.configurationProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG],
                        ).isEqualTo(true)
                    }
                }
            }
        } finally {
            template.destroy()
            factory.destroy()
        }
    }

    companion object {
        @JvmField
        @Container
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
    }
}
