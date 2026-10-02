package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplate
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class InstanceRegisteredEventProducerServiceContextTest {
    private val instanceFlowTemplateFactory: InstanceFlowTemplateFactory = mock()
    private val eventTopicService: EventTopicService = mock()
    private val instanceFlowTemplate: InstanceFlowTemplate<InstanceObjectDto> = mock()
    private val contextRunner =
        ApplicationContextRunner()
            .withBean(InstanceFlowTemplateFactory::class.java, { instanceFlowTemplateFactory })
            .withBean(EventTopicService::class.java, { eventTopicService })
            .withUserConfiguration(InstanceRegisteredEventProducerService::class.java)
            .withPropertyValues("novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d")
            .withInitializer { context ->
                context.beanFactory.conversionService = ApplicationConversionService.getSharedInstance()
            }

    @Test
    fun `producer is absent when feature flag is missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(InstanceRegisteredEventProducerService::class.java)
            verifyNoInteractions(instanceFlowTemplateFactory, eventTopicService)
        }
    }

    @Test
    fun `producer is absent when feature flag is false`() {
        contextRunner.withPropertyValues("novari.flyt.instance-functionality.enabled=false").run { context ->
            assertThat(context).doesNotHaveBean(InstanceRegisteredEventProducerService::class.java)
            verifyNoInteractions(instanceFlowTemplateFactory, eventTopicService)
        }
    }

    @Test
    fun `producer creates template and configures topic when feature flag is true`() {
        whenever(instanceFlowTemplateFactory.createTemplate(InstanceObjectDto::class.java))
            .thenReturn(instanceFlowTemplate)

        contextRunner.withPropertyValues("novari.flyt.instance-functionality.enabled=true").run { context ->
            assertThat(context).hasSingleBean(InstanceRegisteredEventProducerService::class.java)
            verify(instanceFlowTemplateFactory).createTemplate(InstanceObjectDto::class.java)
            verify(eventTopicService).createOrModifyTopic(
                any<EventTopicNameParameters>(),
                any<EventTopicConfiguration>(),
            )
        }
    }
}
