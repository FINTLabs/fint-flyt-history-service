package no.novari.flyt.instance

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.history.EventService
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryEventProducerService
import no.novari.flyt.instance.kafka.InstanceRetryKafkaConfiguration
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.support.serializer.JsonSerializer
import java.time.Clock
import java.util.Optional

class InstanceRetryConditionTest {
    @Test
    fun `master flag wires the retry producer independently of the shared templates`() {
        val topics: EventTopicService = mock()
        val names: TopicNameService = mock { on { validateAndMapToTopicName(any()) }.thenReturn("retry-topic") }
        val mapper = outboxObjectMapper()
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            .withUserConfiguration(
                InstanceRetryController::class.java,
                InstanceRetryService::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
                InstanceRetryKafkaConfiguration::class.java,
            ).withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "fint.application-id=history-app",
                "novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d",
            ).withBean(InstanceService::class.java, { mock() })
            .withBean(EventService::class.java, { mock() })
            .withBean(InstanceErrorEventService::class.java, { mock() })
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withBean(ObjectMapper::class.java, { mapper })
            .withBean(InstanceFlowHeadersMapper::class.java, { InstanceFlowHeadersMapper(mapper) })
            .withBean(EventTopicService::class.java, { topics })
            .withBean(TopicNameService::class.java, { names })
            .withBean(AuditorAware::class.java, { AuditorAware { Optional.of(Actor.System) } })
            .withBean(ProducerConfig::class.java, {
                ProducerConfig(
                    mapOf(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                    ),
                )
            })
            .run {
                assertThat(it)
                    .hasNotFailed()
                    .hasSingleBean(InstanceRetryController::class.java)
                    .hasSingleBean(InstanceRequestedForRetryEventProducerService::class.java)
                    .hasBean("instanceRetryKafkaTemplate")
                verify(topics).createOrModifyTopic(any(), any())
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "novari.flyt.instance-functionality.enabled=false"])
    fun `retry components require the master flag`(property: String) {
        ApplicationContextRunner()
            .withUserConfiguration(
                InstanceRetryController::class.java,
                InstanceRetryService::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
                InstanceRetryKafkaConfiguration::class.java,
            ).withPropertyValues(*listOf(property).filter(String::isNotEmpty).toTypedArray())
            .run {
                assertThat(it)
                    .hasNotFailed()
                    .doesNotHaveBean(InstanceRetryController::class.java)
                    .doesNotHaveBean(InstanceRetryService::class.java)
                    .doesNotHaveBean(InstanceRequestedForRetryEventProducerService::class.java)
                    .doesNotHaveBean("instanceRetryKafkaTemplate")
            }
    }
}
