package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.retryHeaders
import no.novari.flyt.instance.retryInstance
import no.novari.flyt.kafka.instanceflow.headers.instanceFlowHeaders
import no.novari.kafka.starter.producer.OriginHeaderProducerInterceptor.Companion.ORIGIN_APPLICATION_ID_RECORD_HEADER
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.domain.AuditorAware
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.Optional
import java.util.UUID

/** The record mapping-service reads: value, instance flow headers, flyt.actor and a single origin header. */
@Testcontainers(disabledWithoutDocker = true)
class InstanceRequestedForRetryKafkaIntegrationTest {
    @Test
    fun `publishes the existing retry contract and metadata`() {
        val actor = Actor.User(UUID.randomUUID())
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            // NovariKafkaAutoConfiguration and FlytKafkaAutoConfiguration are internal, so they are loaded by name.
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration::class.java,
                    KafkaAutoConfiguration::class.java,
                    Class.forName("no.novari.kafka.starter.autoconfigure.NovariKafkaAutoConfiguration"),
                    Class.forName("no.novari.flyt.kafka.autoconfigure.FlytKafkaAutoConfiguration"),
                ),
            ).withUserConfiguration(
                InstanceRequestedForRetryTopicConfiguration::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
            ).withBean(AuditorAware::class.java, { AuditorAware { Optional.of(actor) } })
            .withPropertyValues(
                "spring.kafka.bootstrap-servers=${kafka.bootstrapServers}",
                "novari.flyt.instance-functionality.enabled=true",
                "novari.kafka.application-id=history-app",
                // Set by kafka-starter's EnvironmentPostProcessor, which an ApplicationContextRunner does not run.
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
                "spring.kafka.producer.properties[interceptor.classes]=no.novari.kafka.starter.producer.OriginHeaderProducerInterceptor",
                "spring.kafka.producer.properties[origin.application.id]=history-app",
                "novari.kafka.topic.org-id=$ORG",
                "novari.kafka.topic.domain-context=flyt",
                "novari.kafka.topic.default-replicas=1",
                "novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d",
            ).run { context ->
                assertThat(context).hasNotFailed()
                val mapper = context.getBean<JsonMapper>()
                val headers = retryHeaders()
                val instance = retryInstance()

                context.getBean<InstanceRequestedForRetryEventProducerService>().publish(headers, instance)

                KafkaConsumer<ByteArray, ByteArray>(
                    mapOf(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                    ),
                ).use { consumer ->
                    val partition = TopicPartition(TOPIC, 0)
                    consumer.assign(listOf(partition))
                    consumer.seekToBeginning(listOf(partition))
                    val record = consumer.poll(Duration.ofSeconds(15)).single()
                    assertThat(record.key()).isNull()
                    assertThat(
                        mapper.readTree(record.value()),
                    ).isEqualTo(mapper.readTree(mapper.writeValueAsBytes(instance)))
                    assertThat(record.headers().instanceFlowHeaders()).isEqualTo(headers)
                    assertThat(
                        ActorHeader.fromHeaderValue(record.headers().lastHeader(ActorHeader.HEADER_NAME).value()),
                    ).isEqualTo(actor)
                    val origins = record.headers().headers(ORIGIN_APPLICATION_ID_RECORD_HEADER).toList()
                    assertThat(origins.map { String(it.value()) }).containsExactly("history-app")
                }
            }
    }

    companion object {
        private const val ORG = "retry-test"
        private const val TOPIC = "$ORG.flyt.event.instance-requested-for-retry"

        @JvmField @Container
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
    }
}
