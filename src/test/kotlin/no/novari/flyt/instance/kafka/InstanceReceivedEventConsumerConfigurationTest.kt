package no.novari.flyt.instance.kafka

import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

class InstanceReceivedEventConsumerConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "false"])
    fun `missing or disabled flag creates no incoming consumer or listener`(flag: String) {
        val runner =
            ApplicationContextRunner().withUserConfiguration(
                InstanceReceivedEventConsumerConfiguration::class.java,
                InstanceReceivedEventListener::class.java,
            )
        val configured =
            if (flag.isEmpty()) {
                runner
            } else {
                runner.withPropertyValues("novari.flyt.instance-functionality.enabled=false")
            }
        configured.run {
            assertThat(it).hasNotFailed()
            assertThat(it).doesNotHaveBean(InstanceReceivedEventListener::class.java)
            assertThat(it).doesNotHaveBean("instanceReceivedEventConsumer")
            assertThat(it).doesNotHaveBean("instanceReceivedConsumerFactory")
        }
    }

    @Test
    fun `consumer overrides only its own offset settings and retains shared connection isolation and group`() {
        val config = consumerConfig()
        val original = config.originals()
        val factory = InstanceReceivedEventConsumerConfiguration().instanceReceivedConsumerFactory(config)
        val actual = factory.configurationProperties
        assertThat(actual[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG]).isEqualTo(false)
        assertThat(actual[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG]).isEqualTo("none")
        assertThat(actual[ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG]).isEqualTo(false)
        assertThat(actual[ConsumerConfig.GROUP_ID_CONFIG]).isEqualTo("history-test-group")
        assertThat(actual[ConsumerConfig.ISOLATION_LEVEL_CONFIG]).isEqualTo("read_committed")
        assertThat(
            actual[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG],
        ).isEqualTo(original[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG])
        assertThat(config.originals()).isEqualTo(original)
    }

    @Test
    fun `enabled consumer is a managed container using record acknowledgements and assignment listener`() {
        val names: TopicNameService = mock()
        whenever(names.validateAndMapToTopicName(any())).thenReturn("received-topic")
        val listener: InstanceReceivedEventListener = mock()
        ApplicationContextRunner()
            .withUserConfiguration(InstanceReceivedEventConsumerConfiguration::class.java)
            .withPropertyValues("novari.flyt.instance-functionality.enabled=true")
            .withBean(ConsumerConfig::class.java, { consumerConfig() })
            .withBean(TopicNameService::class.java, { names })
            .withBean(InstanceReceivedEventListener::class.java, { listener })
            .withBean(InstanceReceivedKafkaProperties::class.java, { InstanceReceivedKafkaProperties() })
            .withInitializer { context ->
                context.beanFactory.addBeanPostProcessor(
                    object : BeanPostProcessor {
                        override fun postProcessBeforeInitialization(
                            bean: Any,
                            beanName: String,
                        ): Any {
                            if (bean is ConcurrentMessageListenerContainer<*, *>) bean.isAutoStartup = false
                            return bean
                        }
                    },
                )
            }.run {
                assertThat(it).hasNotFailed()
                val container =
                    it.getBean(
                        "instanceReceivedEventConsumer",
                        ConcurrentMessageListenerContainer::class.java,
                    )
                assertThat(container.containerProperties.consumerRebalanceListener).isSameAs(listener)
                assertThat(container.containerProperties.ackMode).isEqualTo(ContainerProperties.AckMode.RECORD)
                assertThat(
                    container.containerProperties.assignmentCommitOption,
                ).isEqualTo(ContainerProperties.AssignmentCommitOption.NEVER)
                assertThat(container.containerProperties.isStopImmediate).isTrue()
            }
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, Long.MAX_VALUE])
    fun `retry attempts must be finite and nonnegative`(attempts: Long) {
        assertThatThrownBy { InstanceReceivedKafkaProperties(retryAttempts = attempts) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0])
    fun `retry interval must be positive`(millis: Long) {
        assertThatThrownBy { InstanceReceivedKafkaProperties(retryInterval = Duration.ofMillis(millis)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun consumerConfig(): ConsumerConfig =
        ConsumerConfig(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.GROUP_ID_CONFIG to "history-test-group",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ),
        )
}
