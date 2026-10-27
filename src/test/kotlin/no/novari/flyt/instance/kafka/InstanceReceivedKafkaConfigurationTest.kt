package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceReceivedPositionService
import no.novari.flyt.instance.RECEIVED_TOPIC
import no.novari.flyt.instance.receivedConsumerConfig
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.transaction.PlatformTransactionManager
import java.time.Duration

class InstanceReceivedKafkaConfigurationTest {
    private val components =
        arrayOf(
            InstanceReceivedKafkaConfiguration::class.java,
            InstanceReceivedKafkaMetadataService::class.java,
            InstanceReceivedPositionService::class.java,
            InstanceReceivedOffsetRepository::class.java,
        )

    @ParameterizedTest
    @ValueSource(strings = ["", "false"])
    fun `missing or false master flag creates no new metadata or position components`(flag: String) {
        val runner = ApplicationContextRunner().withUserConfiguration(*components)
        val configured =
            if (flag.isEmpty()) {
                runner
            } else {
                runner.withPropertyValues(
                    "novari.flyt.instance-functionality.enabled=$flag",
                )
            }
        configured.run {
            assertThat(it).hasNotFailed()
            assertThat(it).doesNotHaveBean(InstanceReceivedKafkaMetadataService::class.java)
            assertThat(it).doesNotHaveBean(InstanceReceivedPositionService::class.java)
            assertThat(it).doesNotHaveBean(InstanceReceivedKafkaProperties::class.java)
            assertThat(it).doesNotHaveBean("instanceReceivedMetadataAdminSupplier")
        }
    }

    @Test
    fun `enabled components bind their own timeout without contacting Kafka or initializing positions`() {
        val names: TopicNameService = mock()
        whenever(names.validateAndMapToTopicName(any())).thenReturn(RECEIVED_TOPIC)
        val repository: InstanceReceivedOffsetRepository = mock()
        val config =
            ProducerConfig(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ),
            )
        ApplicationContextRunner()
            .withUserConfiguration(
                *components.filterNot { it == InstanceReceivedOffsetRepository::class.java }.toTypedArray(),
            ).withBean(InstanceReceivedOffsetRepository::class.java, { repository })
            .withBean(ProducerConfig::class.java, { config })
            .withBean(ConsumerConfig::class.java, { receivedConsumerConfig() })
            .withBean(TopicNameService::class.java, { names })
            .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
            .withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "novari.flyt.instance.kafka.metadata-timeout=75ms",
            ).run {
                assertThat(it).hasNotFailed()
                assertThat(it).hasSingleBean(InstanceReceivedKafkaMetadataService::class.java)
                assertThat(it).hasSingleBean(InstanceReceivedPositionService::class.java)
                assertThat(it).doesNotHaveBean(Admin::class.java)
                assertThat(
                    it.getBean(InstanceReceivedKafkaProperties::class.java).metadataTimeout,
                ).isEqualTo(Duration.ofMillis(75))
                verifyNoInteractions(repository)
            }
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 2147483648])
    fun `unsupported metadata timeouts are rejected`(timeoutMs: Long) {
        assertThatThrownBy { InstanceReceivedKafkaProperties(Duration.ofMillis(timeoutMs)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
