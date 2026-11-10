package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplate
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.*

class InstanceDeletedEventProducerServiceTest {
    @Test
    fun `preserves deletion topic and null-payload flow-header contract`() {
        val template: InstanceFlowTemplate<InstanceObjectDto> = mock()
        val factory: InstanceFlowTemplateFactory = mock()
        val topics: EventTopicService = mock()
        whenever(factory.createTemplate(InstanceObjectDto::class.java)).thenReturn(template)
        val producer = InstanceDeletedEventProducerService(factory, topics, Duration.ofDays(4))
        val headers =
            InstanceFlowHeaders
                .builder()
                .instanceId(42L)
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integration")
                .sourceApplicationInstanceId("source-instance")
                .fileIds(listOf(UUID.randomUUID()))
                .correlationId(UUID.randomUUID())
                .build()

        producer.publish(headers)

        val names = argumentCaptor<EventTopicNameParameters>()
        val config = argumentCaptor<EventTopicConfiguration>()
        verify(topics).createOrModifyTopic(names.capture(), config.capture())
        assertThat(names.firstValue.eventName).isEqualTo("instance-deleted")
        assertThat(config.firstValue.partitions).isEqualTo(1)
        assertThat(config.firstValue.retentionTime).isEqualTo(Duration.ofDays(4))
        assertThat(config.firstValue.cleanupFrequency).isEqualTo(EventCleanupFrequency.NORMAL)
        val record = argumentCaptor<InstanceFlowProducerRecord<InstanceObjectDto>>()
        verify(template).send(record.capture())
        assertThat(record.firstValue.instanceFlowHeaders).isSameAs(headers)
        assertThat(record.firstValue.value).isNull()
        assertThat(record.firstValue.key).isNull()
        assertThat(record.firstValue.topicNameParameters).isEqualTo(names.firstValue)
    }
}
