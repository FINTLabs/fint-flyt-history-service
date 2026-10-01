package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
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
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.DefaultErrorHandler
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

class EventListenerConfigurationTest {
    private val eventRepository: EventRepository = mock()
    private val instanceErrorEventService: InstanceErrorEventService = mock()
    private val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService = mock()
    private val errorHandlerFactory: ErrorHandlerFactory = mock()
    private val beanFactory: ConfigurableListableBeanFactory = mock()
    private val instanceFlowHeadersMappingService = InstanceFlowHeadersMappingService()
    private val actorDuringSave = mutableListOf<Actor?>()

    private lateinit var infoListenersByEventName: Map<String, Consumer<InstanceFlowConsumerRecord<Any>>>
    private lateinit var errorListenersByEventName: Map<String, Consumer<InstanceFlowConsumerRecord<ErrorCollection>>>
    private lateinit var eventListenerContainers: Map<String, ConcurrentMessageListenerContainer<String, *>>

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

        eventListenerContainers = createEventListenerConfiguration().eventListenerContainers()

        infoListenersByEventName =
            infoTopicNameParametersCaptor
                .allValues
                .zip(infoListenerCaptor.allValues)
                .associate { (topicNameParameters, listener) -> topicNameParameters.eventName to listener }

        errorListenersByEventName =
            EventCategory.entries
                .filter { it.type == EventType.ERROR && it.createKafkaListener }
                .zip(errorListenerCaptor.allValues)
                .associate { (category, listener) -> category.eventName to listener }
    }

    @Test
    fun `legacy error topic listeners are enabled by default`() {
        assertThat(eventListenerContainers.keys).containsAll(legacyErrorEventNames())
    }

    @Test
    fun `legacy error topic listeners can be disabled`() {
        val containers =
            createEventListenerConfiguration(legacyErrorTopicListenersEnabled = false)
                .eventListenerContainers()

        assertThat(containers.keys)
            .doesNotContainAnyElementsOf(legacyErrorEventNames())
            .contains(EventCategory.INSTANCE_RECEIVAL_ERROR.eventName)
    }

    @Test
    fun `legacy error listener delegates record data without saving directly`() {
        val category = EventCategory.INSTANCE_REGISTRATION_ERROR
        val errors =
            ErrorCollection(
                listOf(Error("test-error", mapOf("key" to "value"))),
            )
        val record = errorConsumerRecord(category.eventName, errors)

        errorListenersByEventName.getValue(category.eventName).accept(record)

        verify(instanceErrorEventService).registerError(
            same(record.instanceFlowHeaders),
            eq(category),
            same(errors),
            eq(Instant.ofEpochMilli(record.consumerRecord.timestamp())),
            eq("test-app"),
        )
        verify(eventRepository, never()).save(any<EventEntity>())
    }

    @Test
    fun `instance error listener remains active with legacy disabled and delegates record data`() {
        val listenerContainerFactory: ParameterizedListenerContainerFactory<InstanceErrorEvent> = mock()
        val listenerContainer: ConcurrentMessageListenerContainer<String, InstanceErrorEvent> = mock()
        val listenerCaptor = argumentCaptor<Consumer<InstanceFlowConsumerRecord<InstanceErrorEvent>>>()
        whenever(
            instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                eq(InstanceErrorEvent::class.java),
                listenerCaptor.capture(),
                any(),
                any(),
            ),
        ).thenReturn(listenerContainerFactory)
        whenever(listenerContainerFactory.createContainer(any<ErrorEventTopicNameParameters>()))
            .thenReturn(listenerContainer)

        createEventListenerConfiguration(legacyErrorTopicListenersEnabled = false).instanceErrorListener()

        val event =
            InstanceErrorEvent(
                name = InstanceErrorOrigin.REGISTRATION,
                errors =
                    ErrorCollection(
                        listOf(Error("test-error", null)),
                    ),
            )
        val record = errorConsumerRecord("instance-error", event)
        listenerCaptor.firstValue.accept(record)

        verify(instanceErrorEventService).registerError(
            same(record.instanceFlowHeaders),
            same(event),
            eq(Instant.ofEpochMilli(record.consumerRecord.timestamp())),
            eq("test-app"),
        )
        verify(eventRepository, never()).save(any<EventEntity>())
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

    private fun <T> errorConsumerRecord(
        eventName: String,
        value: T,
    ): InstanceFlowConsumerRecord<T> {
        val kafkaHeaders = RecordHeaders()
        kafkaHeaders.add(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER, "test-app".toByteArray())
        val consumerRecord =
            ConsumerRecord(
                eventName,
                0,
                0L,
                1234L,
                TimestampType.CREATE_TIME,
                0,
                0,
                "key",
                value,
                kafkaHeaders,
                Optional.empty(),
            )
        return InstanceFlowConsumerRecord
            .builder<T>()
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

    private fun createEventListenerConfiguration(
        legacyErrorTopicListenersEnabled: Boolean = true,
    ): EventListenerConfiguration =
        EventListenerConfiguration(
            eventRepository = eventRepository,
            instanceErrorEventService = instanceErrorEventService,
            instanceFlowListenerFactoryService = instanceFlowListenerFactoryService,
            instanceFlowHeadersMappingService = instanceFlowHeadersMappingService,
            errorHandlerFactory = errorHandlerFactory,
            beanFactory = beanFactory,
            legacyErrorTopicListenersEnabled = legacyErrorTopicListenersEnabled,
        )

    private fun legacyErrorEventNames(): Set<String> =
        setOf(
            EventCategory.INSTANCE_REGISTRATION_ERROR.eventName,
            EventCategory.INSTANCE_RETRY_REQUEST_ERROR.eventName,
            EventCategory.INSTANCE_MAPPING_ERROR.eventName,
            EventCategory.INSTANCE_DISPATCHING_ERROR.eventName,
        )
}
