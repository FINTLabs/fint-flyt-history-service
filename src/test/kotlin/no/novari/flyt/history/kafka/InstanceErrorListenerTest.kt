package no.novari.flyt.history.kafka

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@Import(InstanceFlowHeadersMappingService::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InstanceErrorListenerTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService

    private lateinit var listener: Consumer<InstanceFlowConsumerRecord<InstanceErrorEvent>>

    @BeforeEach
    fun setUp() {
        eventRepository.deleteAll()

        val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService = mock()
        val errorHandlerFactory: ErrorHandlerFactory = mock()
        val listenerContainerFactory: ParameterizedListenerContainerFactory<InstanceErrorEvent> = mock()
        val listenerContainer: ConcurrentMessageListenerContainer<String, InstanceErrorEvent> = mock()
        val errorHandler: DefaultErrorHandler = mock()

        val listenerCaptor = argumentCaptor<Consumer<InstanceFlowConsumerRecord<InstanceErrorEvent>>>()

        whenever(
            errorHandlerFactory.createErrorHandler(any<ErrorHandlerConfiguration<InstanceErrorEvent>>()),
        ).thenReturn(errorHandler)
        whenever(
            instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                any(),
                listenerCaptor.capture(),
                any(),
                any(),
            ),
        ).thenReturn(listenerContainerFactory)
        whenever(listenerContainerFactory.createContainer(any<ErrorEventTopicNameParameters>()))
            .thenReturn(listenerContainer)

        EventListenerConfiguration(
            eventRepository = eventRepository,
            instanceFlowListenerFactoryService = instanceFlowListenerFactoryService,
            instanceFlowHeadersMappingService = instanceFlowHeadersMappingService,
            errorHandlerFactory = errorHandlerFactory,
            beanFactory = mock<ConfigurableListableBeanFactory>(),
        ).instanceErrorListener()

        listener = listenerCaptor.firstValue
    }

    @ParameterizedTest
    @EnumSource(InstanceErrorOrigin::class)
    fun `listener persists EventEntity with correct name for each InstanceErrorOrigin`(origin: InstanceErrorOrigin) {
        val expectedCategory = expectedCategory(origin)
        val errors =
            ErrorCollection(
                listOf(
                    no.novari.flyt.kafka.model
                        .Error("test-error", mapOf("key" to "val")),
                ),
            )
        val event = InstanceErrorEvent(name = origin, errors = errors)

        listener.accept(instanceErrorConsumerRecord(event))

        val saved = eventRepository.findAll()
        assertThat(saved).hasSize(1)
        val entity = saved.single()
        assertThat(entity.name).isEqualTo(expectedCategory.eventName)
        assertThat(entity.type).isEqualTo(EventType.ERROR)
        assertThat(entity.errors).hasSize(1)
        assertThat(entity.errors.first().errorCode).isEqualTo("test-error")
    }

    private fun expectedCategory(origin: InstanceErrorOrigin): EventCategory =
        when (origin) {
            InstanceErrorOrigin.RECEIVAL -> EventCategory.INSTANCE_RECEIVAL_ERROR
            InstanceErrorOrigin.REGISTRATION -> EventCategory.INSTANCE_REGISTRATION_ERROR
            InstanceErrorOrigin.RETRY_REQUEST -> EventCategory.INSTANCE_RETRY_REQUEST_ERROR
            InstanceErrorOrigin.MAPPING -> EventCategory.INSTANCE_MAPPING_ERROR
            InstanceErrorOrigin.DISPATCHING -> EventCategory.INSTANCE_DISPATCHING_ERROR
        }

    private fun instanceErrorConsumerRecord(event: InstanceErrorEvent): InstanceFlowConsumerRecord<InstanceErrorEvent> {
        val instanceFlowHeaders =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("sa-integration-1")
                .sourceApplicationInstanceId("sa-instance-1")
                .correlationId(UUID.randomUUID())
                .integrationId(100L)
                .build()
        val kafkaHeaders = RecordHeaders()
        kafkaHeaders.add(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER, "test-app".toByteArray())
        val consumerRecord =
            ConsumerRecord(
                "instance-error",
                0,
                0L,
                0L,
                TimestampType.CREATE_TIME,
                0,
                0,
                "key",
                event,
                kafkaHeaders,
                Optional.empty(),
            )
        return InstanceFlowConsumerRecord
            .builder<InstanceErrorEvent>()
            .instanceFlowHeaders(instanceFlowHeaders)
            .consumerRecord(consumerRecord)
            .build()
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(8).toBytes())
                }

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }
    }
}
