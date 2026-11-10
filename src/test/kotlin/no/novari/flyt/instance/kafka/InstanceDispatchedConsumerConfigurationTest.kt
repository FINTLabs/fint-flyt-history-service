package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceCleanupService
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.util.UUID
import java.util.function.Consumer

class InstanceDispatchedConsumerConfigurationTest {
    @Test
    fun `dispatch is consumed independently of the history listener and passes original headers to deletion`() {
        val factory: InstanceFlowListenerFactoryService = mock()
        val containers: ParameterizedListenerContainerFactory<InstanceObjectDto> = mock()
        val container: ConcurrentMessageListenerContainer<String, InstanceObjectDto> = mock()
        val deletion: InstanceDeletionService = mock()
        val listener = argumentCaptor<Consumer<InstanceFlowConsumerRecord<InstanceObjectDto>>>()
        val config = argumentCaptor<ListenerConfiguration>()
        val topic = argumentCaptor<EventTopicNameParameters>()
        whenever(
            factory.createRecordListenerContainerFactory(
                eq(InstanceObjectDto::class.java),
                listener.capture(),
                config.capture(),
                any(),
            ),
        ).thenReturn(containers)
        whenever(containers.createContainer(topic.capture())).thenReturn(container)

        InstanceDispatchedConsumerConfiguration().instanceDispatchedConsumer(factory, deletion, ErrorHandlerFactory())
        val headers =
            InstanceFlowHeaders
                .builder()
                .instanceId(42L)
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integration")
                .sourceApplicationInstanceId("source-instance")
                .correlationId(UUID.randomUUID())
                .build()
        listener.firstValue.accept(
            InstanceFlowConsumerRecord
                .builder<InstanceObjectDto>()
                .instanceFlowHeaders(headers)
                .consumerRecord(ConsumerRecord("instance-dispatched", 0, 0L, "key", InstanceObjectDto()))
                .build(),
        )

        assertThat(config.firstValue.groupIdSuffix).contains("-instance-dispatched")
        assertThat(topic.firstValue.eventName).isEqualTo("instance-dispatched")
        verify(deletion).delete(headers)
    }

    @Test
    fun `deletion functionality creates no beans or Kafka collaborators before cutover`() {
        val runner =
            ApplicationContextRunner().withUserConfiguration(
                InstanceDispatchedConsumerConfiguration::class.java,
                InstanceDeletedEventProducerService::class.java,
                InstanceDeletionService::class.java,
                InstanceCleanupService::class.java,
            )
        for (properties in listOf(emptyArray(), arrayOf("novari.flyt.instance-functionality.enabled=false"))) {
            runner.withPropertyValues(*properties).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(InstanceDispatchedConsumerConfiguration::class.java)
                assertThat(context).doesNotHaveBean(InstanceDeletedEventProducerService::class.java)
                assertThat(context).doesNotHaveBean(InstanceDeletionService::class.java)
                assertThat(context).doesNotHaveBean(InstanceCleanupService::class.java)
            }
        }
    }
}
