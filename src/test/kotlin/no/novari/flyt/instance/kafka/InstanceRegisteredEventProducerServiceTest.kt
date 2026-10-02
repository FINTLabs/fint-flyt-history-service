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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.UUID

class InstanceRegisteredEventProducerServiceTest {
    private val instanceFlowTemplateFactory: InstanceFlowTemplateFactory = mock()
    private val eventTopicService: EventTopicService = mock()
    private val instanceFlowTemplate: InstanceFlowTemplate<InstanceObjectDto> = mock()
    private lateinit var producer: InstanceRegisteredEventProducerService

    @BeforeEach
    fun setUp() {
        whenever(instanceFlowTemplateFactory.createTemplate(InstanceObjectDto::class.java))
            .thenReturn(instanceFlowTemplate)
        producer =
            InstanceRegisteredEventProducerService(
                instanceFlowTemplateFactory,
                eventTopicService,
                Duration.ofDays(4),
            )
    }

    @Test
    fun `creates DTO template and configures instance registered topic`() {
        verify(instanceFlowTemplateFactory).createTemplate(InstanceObjectDto::class.java)

        val topicCaptor = argumentCaptor<EventTopicNameParameters>()
        val configurationCaptor = argumentCaptor<EventTopicConfiguration>()
        verify(eventTopicService).createOrModifyTopic(topicCaptor.capture(), configurationCaptor.capture())

        assertThat(topicCaptor.firstValue.eventName).isEqualTo("instance-registered")
        assertThat(configurationCaptor.firstValue.partitions).isEqualTo(1)
        assertThat(configurationCaptor.firstValue.retentionTime).isEqualTo(Duration.ofDays(4))
        assertThat(configurationCaptor.firstValue.cleanupFrequency).isEqualTo(EventCleanupFrequency.NORMAL)
    }

    @Test
    fun `publish sends one record with unchanged headers and DTO`() {
        val headers =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
                .instanceId(123L)
                .build()
        val instance = InstanceObjectDto(id = 123L, valuePerKey = mutableMapOf("key" to "value"))

        producer.publish(headers, instance)

        val recordCaptor = argumentCaptor<InstanceFlowProducerRecord<InstanceObjectDto>>()
        verify(instanceFlowTemplate).send(recordCaptor.capture())
        val record = recordCaptor.firstValue
        assertThat(record.instanceFlowHeaders).isSameAs(headers)
        assertThat(record.value).isSameAs(instance)
        val topicCaptor = argumentCaptor<EventTopicNameParameters>()
        verify(eventTopicService).createOrModifyTopic(topicCaptor.capture(), any<EventTopicConfiguration>())
        assertThat(record.topicNameParameters).isSameAs(topicCaptor.firstValue)
    }
}
