package no.novari.flyt.instance

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.history.EventService
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.instance.config.InstanceRetryConfiguration
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryEventProducerService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.topic.EventTopicService
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
import java.time.Clock
import java.util.Optional

class InstanceRetryConditionTest {
    @Test
    fun `master flag wires the retry producer through the library factory`() {
        val topics: EventTopicService = mock()
        val factory: InstanceFlowTemplateFactory =
            mock {
                on { createTemplate(InstanceObjectDto::class.java) }.thenReturn(mock())
            }
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            .withUserConfiguration(
                InstanceRetryController::class.java,
                InstanceRetryService::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
                InstanceRetryConfiguration::class.java,
            ).withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "fint.application-id=history-app",
                "novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d",
            ).withBean(InstanceService::class.java, { mock() })
            .withBean(EventService::class.java, { mock() })
            .withBean(InstanceErrorEventService::class.java, { mock() })
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withBean(InstanceFlowTemplateFactory::class.java, { factory })
            .withBean(EventTopicService::class.java, { topics })
            .withBean(AuditorAware::class.java, { AuditorAware { Optional.of(Actor.System) } })
            .run {
                assertThat(it)
                    .hasNotFailed()
                    .hasSingleBean(InstanceRetryController::class.java)
                    .hasSingleBean(InstanceRequestedForRetryEventProducerService::class.java)
                    .doesNotHaveBean("instanceRetryKafkaTemplate")
                verify(topics).createOrModifyTopic(any(), any())
                verify(factory).createTemplate(InstanceObjectDto::class.java)
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
                InstanceRetryConfiguration::class.java,
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
