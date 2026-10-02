package no.novari.flyt.history.kafka

import no.novari.kafka.topic.ErrorEventTopicService
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class InstanceReceivalErrorConfigurationTest {
    private val errorEventTopicService: ErrorEventTopicService = mock()
    private val contextRunner =
        ApplicationContextRunner()
            .withBean(ErrorEventTopicService::class.java, { errorEventTopicService })
            .withUserConfiguration(InstanceReceivalErrorConfiguration::class.java)
            .withPropertyValues("novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d")
            .withInitializer { context ->
                context.beanFactory.conversionService = ApplicationConversionService.getSharedInstance()
            }

    @Test
    fun `instance receival error topic is not configured when legacy listeners are disabled`() {
        contextRunner
            .withPropertyValues("novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(InstanceReceivalErrorConfiguration::class.java)
                verifyNoInteractions(errorEventTopicService)
            }
    }

    @Test
    fun `instance receival error topic is configured when legacy listeners are enabled`() {
        contextRunner
            .withPropertyValues("novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled=true")
            .run { context ->
                assertThat(context).hasSingleBean(InstanceReceivalErrorConfiguration::class.java)
                verify(errorEventTopicService).createOrModifyTopic(
                    any<ErrorEventTopicNameParameters>(),
                    any<EventTopicConfiguration>(),
                )
            }
    }

    @Test
    fun `instance receival error topic is configured by default`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(InstanceReceivalErrorConfiguration::class.java)
            verify(errorEventTopicService).createOrModifyTopic(
                any<ErrorEventTopicNameParameters>(),
                any<EventTopicConfiguration>(),
            )
        }
    }
}
