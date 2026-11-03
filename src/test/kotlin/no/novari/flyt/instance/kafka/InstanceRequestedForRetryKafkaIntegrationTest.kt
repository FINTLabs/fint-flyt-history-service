package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.producing.ParameterizedTemplateFactory
import no.novari.kafka.producing.ProducerFactory
import no.novari.kafka.producing.TemplateFactory
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Optional
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
class InstanceRequestedForRetryKafkaIntegrationTest {
    @Test
    fun `broker acknowledges the existing retry contract and metadata`() {
        val mapper = outboxObjectMapper()
        val headersMapper = InstanceFlowHeadersMapper(mapper)
        val config =
            ProducerConfig(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                    ProducerConfig.INTERCEPTOR_CLASSES_CONFIG to
                        listOf(OriginHeaderProducerInterceptor::class.java.name),
                    OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_PRODUCER_CONFIG to "history-app",
                ),
            )
        val properties = InstanceRetryProperties()
        val factories = mutableListOf<DefaultKafkaProducerFactory<*, *>>()
        val libraryProducerFactory = spy(ProducerFactory(config, mapper))
        doAnswer { invocation ->
            (invocation.callRealMethod() as DefaultKafkaProducerFactory<*, *>).also { factories.add(it) }
        }.whenever(libraryProducerFactory).createFactory(InstanceObjectDto::class.java)
        val topic = "retry-${UUID.randomUUID()}"
        try {
            Admin.create(mapOf("bootstrap.servers" to kafka.bootstrapServers)).use {
                it.createTopics(listOf(NewTopic(topic, 1, 1))).all().get()
            }
            val names: TopicNameService = mock()
            whenever(names.validateAndMapToTopicName(any())).thenReturn(topic)
            val actor = Actor.User(UUID.randomUUID())
            ApplicationContextRunner()
                .withUserConfiguration(
                    TemplateFactory::class.java,
                    ParameterizedTemplateFactory::class.java,
                    InstanceFlowTemplateFactory::class.java,
                ).withBean(ProducerFactory::class.java, { libraryProducerFactory })
                .withBean(TopicNameService::class.java, { names })
                .withBean(InstanceFlowHeadersMapper::class.java, { headersMapper })
                .run { context ->
                    assertThat(context).hasNotFailed()
                    val producer =
                        InstanceRequestedForRetryEventProducerService(
                            context.getBean(InstanceFlowTemplateFactory::class.java),
                            mock(),
                            AuditorAware { Optional.of(actor) },
                            properties,
                            Duration.ofDays(4),
                        )
                    val fixture = outboxEvent()
                    producer.publish(fixture.headers, fixture.instance)
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
                        val record = consumer.poll(Duration.ofSeconds(15)).single()
                        assertThat(record.key()).isNull()
                        assertThat(
                            mapper.readTree(record.value()),
                        ).isEqualTo(mapper.readTree(mapper.writeValueAsBytes(fixture.instance)))
                        assertThat(headersMapper.getInstanceFlowHeaders(record.headers())).isEqualTo(fixture.headers)
                        assertThat(
                            ActorHeader.fromHeaderValue(record.headers().lastHeader(ActorHeader.HEADER_NAME).value()),
                        ).isEqualTo(actor)
                        val origins =
                            record
                                .headers()
                                .headers(
                                    OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                                ).toList()
                        assertThat(origins).hasSize(1)
                        assertThat(String(origins.single().value())).isEqualTo("history-app")
                    }
                }
        } finally {
            factories.forEach { it.destroy() }
        }
    }

    companion object {
        @JvmField @Container
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
    }
}
