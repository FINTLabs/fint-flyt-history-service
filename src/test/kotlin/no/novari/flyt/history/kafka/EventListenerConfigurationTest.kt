package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.DefaultErrorHandler
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

class EventListenerConfigurationTest {
    private val eventRepository: EventRepository = mock()
    private val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService = mock()
    private val errorHandlerFactory: ErrorHandlerFactory = mock()
    private val beanFactory: ConfigurableListableBeanFactory = mock()
    private val instanceFlowHeadersMappingService = InstanceFlowHeadersMappingService()
    private val actorDuringSave = mutableListOf<Actor?>()

    private lateinit var infoListenersByEventName: Map<String, Consumer<InstanceFlowConsumerRecord<Any>>>

    @BeforeEach
    fun setUp() {
        actorDuringSave.clear()
        whenever(eventRepository.save(any<EventEntity>())).thenAnswer { invocation ->
            actorDuringSave.add(ActorContext.currentActor())
            invocation.getArgument(0)
        }

        val infoListenerContainerFactory: ParameterizedListenerContainerFactory<Any> = mock()
        val errorListenerContainerFactory: ParameterizedListenerContainerFactory<ErrorCollection> = mock()
        val infoListenerContainer: ConcurrentMessageListenerContainer<String, Any> = mock()
        val errorListenerContainer: ConcurrentMessageListenerContainer<String, ErrorCollection> = mock()
        val errorHandler: DefaultErrorHandler = mock()

        val infoListenerCaptor = argumentCaptor<Consumer<InstanceFlowConsumerRecord<Any>>>()
        val infoTopicNameParametersCaptor = argumentCaptor<EventTopicNameParameters>()
        val errorListenerCaptor = argumentCaptor<Consumer<InstanceFlowConsumerRecord<ErrorCollection>>>()

        whenever(errorHandlerFactory.createErrorHandler(any<ErrorHandlerConfiguration<Any>>())).thenReturn(errorHandler)
        whenever(
            instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                eq(Any::class.java),
                infoListenerCaptor.capture(),
                any(),
                any(),
            ),
        ).thenReturn(infoListenerContainerFactory)
        whenever(
            instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                eq(ErrorCollection::class.java),
                errorListenerCaptor.capture(),
                any(),
                any(),
            ),
        ).thenReturn(errorListenerContainerFactory)
        whenever(infoListenerContainerFactory.createContainer(infoTopicNameParametersCaptor.capture()))
            .thenReturn(infoListenerContainer)
        whenever(errorListenerContainerFactory.createContainer(any<ErrorEventTopicNameParameters>()))
            .thenReturn(errorListenerContainer)

        EventListenerConfiguration(
            eventRepository = eventRepository,
            instanceFlowListenerFactoryService = instanceFlowListenerFactoryService,
            instanceFlowHeadersMappingService = instanceFlowHeadersMappingService,
            errorHandlerFactory = errorHandlerFactory,
            beanFactory = beanFactory,
        ).eventListenerContainers()

        infoListenersByEventName =
            infoTopicNameParametersCaptor
                .allValues
                .zip(infoListenerCaptor.allValues)
                .associate { (topicNameParameters, listener) -> topicNameParameters.eventName to listener }
    }

    @Test
    fun `instance requested for retry listener saves event with actor from flyt actor header`() {
        val actor = Actor.User(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))

        infoListenersByEventName
            .getValue(EventCategory.INSTANCE_REQUESTED_FOR_RETRY.eventName)
            .accept(instanceFlowConsumerRecord(EventCategory.INSTANCE_REQUESTED_FOR_RETRY, actor))

        assertThat(actorDuringSave).containsExactly(actor)
        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `instance requested for retry listener leaves default auditor fallback when flyt actor header is missing`() {
        infoListenersByEventName
            .getValue(EventCategory.INSTANCE_REQUESTED_FOR_RETRY.eventName)
            .accept(instanceFlowConsumerRecord(EventCategory.INSTANCE_REQUESTED_FOR_RETRY, actor = null))

        assertThat(actorDuringSave).containsExactly(null)
    }

    @Test
    fun `other info event listeners ignore flyt actor header`() {
        val actor = Actor.User(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))

        infoListenersByEventName
            .getValue(EventCategory.INSTANCE_MAPPED.eventName)
            .accept(instanceFlowConsumerRecord(EventCategory.INSTANCE_MAPPED, actor))

        assertThat(actorDuringSave).containsExactly(null)
    }

    private fun instanceFlowConsumerRecord(
        eventCategory: EventCategory,
        actor: Actor?,
    ): InstanceFlowConsumerRecord<Any> {
        val kafkaHeaders = RecordHeaders()
        kafkaHeaders.add(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER, "test-app".toByteArray())
        if (actor != null) {
            kafkaHeaders.add(ActorHeader.HEADER_NAME, ActorHeader.toHeaderValue(actor))
        }

        val consumerRecord =
            ConsumerRecord(
                eventCategory.eventName,
                0,
                0L,
                0L,
                TimestampType.CREATE_TIME,
                0,
                0,
                "key",
                Any(),
                kafkaHeaders,
                Optional.empty(),
            )

        return InstanceFlowConsumerRecord
            .builder<Any>()
            .instanceFlowHeaders(instanceFlowHeaders())
            .consumerRecord(consumerRecord)
            .build()
    }

    private fun instanceFlowHeaders(): InstanceFlowHeaders =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("sa-integration-1")
            .sourceApplicationInstanceId("sa-instance-1")
            .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
            .integrationId(100L)
            .build()
}
