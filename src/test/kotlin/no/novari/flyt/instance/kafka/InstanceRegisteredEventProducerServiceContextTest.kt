package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxProperties
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.core.KafkaTemplate

class InstanceRegisteredEventProducerServiceContextTest {
    private val template: KafkaTemplate<String, InstanceObjectDto> = mock()
    private val admin: Admin = mock()
    private val topics: EventTopicService = mock()
    private val names: TopicNameService = mock()
    private val runner =
        ApplicationContextRunner()
            .withBean("instanceRegisteredOutboxKafkaTemplate", KafkaTemplate::class.java, { template })
            .withBean("instanceRegisteredOutboxAdmin", Admin::class.java, { admin })
            .withBean(EventTopicService::class.java, { topics })
            .withBean(TopicNameService::class.java, { names })
            .withBean(InstanceFlowHeadersMapper::class.java, { InstanceFlowHeadersMapper(outboxObjectMapper()) })
            .withBean(InstanceRegisteredOutboxProperties::class.java, { InstanceRegisteredOutboxProperties() })
            .withUserConfiguration(InstanceRegisteredEventProducerService::class.java)
            .withPropertyValues("novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d")
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }

    @Test
    fun `missing flag does not create a producer or contact Kafka`() {
        runner.run {
            assertThat(it).doesNotHaveBean(InstanceRegisteredEventProducerService::class.java)
            verifyNoInteractions(admin, topics, names)
            verify(template, never()).send(any<ProducerRecord<String, InstanceObjectDto>>())
        }
    }

    @Test
    fun `false flag does not create a producer or contact Kafka`() {
        runner.withPropertyValues("novari.flyt.instance-functionality.enabled=false").run {
            assertThat(it).doesNotHaveBean(InstanceRegisteredEventProducerService::class.java)
            verifyNoInteractions(admin, topics, names)
            verify(template, never()).send(any<ProducerRecord<String, InstanceObjectDto>>())
        }
    }

    @Test
    fun `true flag registers the producer with the existing topic name`() {
        whenever(names.validateAndMapToTopicName(any())).thenReturn("registered-test")
        runner.withPropertyValues("novari.flyt.instance-functionality.enabled=true").run {
            assertThat(it).hasSingleBean(InstanceRegisteredEventProducerService::class.java)
            verify(topics).createOrModifyTopic(any<EventTopicNameParameters>(), any())
        }
    }
}
