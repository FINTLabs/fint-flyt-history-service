package no.novari.flyt.instance

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.history.AuthorizationService
import no.novari.flyt.history.EventService
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryEventProducerService
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryTopicConfiguration
import no.novari.kafka.starter.topic.EventTopicName
import org.apache.kafka.clients.admin.NewTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.domain.AuditorAware
import java.time.Clock
import java.util.Optional

class InstanceRetryConditionTest {
    @Test
    fun `master flag wires the retry producer and its topic`() {
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            // NovariKafkaAutoConfiguration is internal in kafka-starter, so it is loaded by name.
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration::class.java,
                    KafkaAutoConfiguration::class.java,
                    Class.forName("no.novari.kafka.starter.autoconfigure.NovariKafkaAutoConfiguration"),
                ),
            ).withUserConfiguration(
                InstanceRetryController::class.java,
                InstanceRetryService::class.java,
                InstanceRequestedForRetryTopicConfiguration::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
            ).withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "fint.application-id=history-app",
                "novari.kafka.topic.org-id=test-org",
                "novari.kafka.topic.domain-context=flyt",
                "novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d",
            ).withBean(InstanceService::class.java, { mock() })
            .withBean(EventService::class.java, { mock() })
            .withBean(InstanceErrorEventService::class.java, { mock() })
            .withBean(AuthorizationService::class.java, { mock() })
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withBean(AuditorAware::class.java, { AuditorAware { Optional.of(Actor.System) } })
            .run {
                assertThat(it)
                    .hasNotFailed()
                    .hasSingleBean(InstanceRetryController::class.java)
                    .hasSingleBean(InstanceRequestedForRetryEventProducerService::class.java)
                assertThat(it.getBean<EventTopicName>("instanceRequestedForRetryTopic").name)
                    .isEqualTo("test-org.flyt.event.instance-requested-for-retry")
                assertThat(it.getBean<NewTopic>("instanceRequestedForRetryTopicCreation").configs())
                    .containsEntry("retention.ms", "345600000")
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "novari.flyt.instance-functionality.enabled=false"])
    fun `retry components require the master flag`(property: String) {
        ApplicationContextRunner()
            .withUserConfiguration(
                InstanceRetryController::class.java,
                InstanceRetryService::class.java,
                InstanceRequestedForRetryTopicConfiguration::class.java,
                InstanceRequestedForRetryEventProducerService::class.java,
            ).withPropertyValues(*listOf(property).filter(String::isNotEmpty).toTypedArray())
            .run {
                assertThat(it)
                    .hasNotFailed()
                    .doesNotHaveBean(InstanceRetryController::class.java)
                    .doesNotHaveBean(InstanceRetryService::class.java)
                    .doesNotHaveBean(InstanceRequestedForRetryEventProducerService::class.java)
            }
    }
}
