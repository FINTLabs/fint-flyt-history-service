package no.novari.flyt.history.kafka

import no.novari.flyt.history.ClockConfiguration
import no.novari.flyt.history.ErrorScrubService
import no.novari.flyt.history.EventScrubber
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowErrorHandlerConfiguration
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowErrorHandlerFactory
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.function.Consumer

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@Import(ErrorScrubService::class, EventScrubber::class, JpaAuditingTestConfig::class, ClockConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = ["novari.flyt.history-service.retention.scrub-batch-size=2"])
class InstanceDeletedConsumerConfigurationTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var errorScrubService: ErrorScrubService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var listener: Consumer<InstanceFlowConsumerRecord<Any>>
    private lateinit var listenerConfiguration: ListenerConfiguration
    private lateinit var topicNameParameters: EventTopicNameParameters

    @BeforeEach
    fun setUp() {
        eventRepository.deleteAll()

        val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService = mock()
        val instanceFlowErrorHandlerFactory: InstanceFlowErrorHandlerFactory = mock()
        val listenerContainerFactory: ParameterizedListenerContainerFactory<Any> = mock()
        val listenerContainer: ConcurrentMessageListenerContainer<String, Any> = mock()
        val errorHandler: DefaultErrorHandler = mock()

        val listenerCaptor = argumentCaptor<Consumer<InstanceFlowConsumerRecord<Any>>>()
        val listenerConfigurationCaptor = argumentCaptor<ListenerConfiguration>()
        val topicNameParametersCaptor = argumentCaptor<EventTopicNameParameters>()

        whenever(
            instanceFlowErrorHandlerFactory.createErrorHandler(
                any<InstanceFlowErrorHandlerConfiguration<Any>>(),
            ),
        ).thenReturn(errorHandler)
        whenever(
            instanceFlowListenerFactoryService.createRecordListenerContainerFactory(
                eq(Any::class.java),
                listenerCaptor.capture(),
                listenerConfigurationCaptor.capture(),
                any(),
            ),
        ).thenReturn(listenerContainerFactory)
        whenever(
            listenerContainerFactory.createContainer(topicNameParametersCaptor.capture()),
        ).thenReturn(listenerContainer)

        InstanceDeletedConsumerConfiguration(
            instanceFlowListenerFactoryService = instanceFlowListenerFactoryService,
            instanceFlowErrorHandlerFactory = instanceFlowErrorHandlerFactory,
        ).instanceDeletedConsumer(errorScrubService)

        listener = listenerCaptor.firstValue
        listenerConfiguration = listenerConfigurationCaptor.firstValue
        topicNameParameters = topicNameParametersCaptor.firstValue
    }

    @Test
    fun `consumer scrubs matching events and leaves scrubbed timestamp unchanged on repeated consume`() {
        val matchingHeaders = headers(1L, "sa-integration-1", "sa-instance-1")
        val firstMatchingError =
            errorEvent(
                sourceApplicationId = 1L,
                sourceApplicationIntegrationId = "sa-integration-1",
                sourceApplicationInstanceId = "sa-instance-1",
                name = EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                timestamp = odt(2024, 1, 1, 12, 0),
                args = mapOf("filename" to "document.pdf", "reason" to "bad-payload"),
            )
        val secondMatchingError =
            errorEvent(
                sourceApplicationId = 1L,
                sourceApplicationIntegrationId = "sa-integration-1",
                sourceApplicationInstanceId = "sa-instance-1",
                name = EventCategory.INSTANCE_REGISTRATION_ERROR.eventName,
                timestamp = odt(2024, 1, 1, 12, 1),
                args = mapOf("archiveId" to "archive-123"),
            )
        val matchingInfo =
            infoEvent(
                sourceApplicationId = 1L,
                sourceApplicationIntegrationId = "sa-integration-1",
                sourceApplicationInstanceId = "sa-instance-1",
                name = EventCategory.INSTANCE_REGISTERED.eventName,
                timestamp = odt(2024, 1, 1, 12, 2),
            )
        val otherError =
            errorEvent(
                sourceApplicationId = 2L,
                sourceApplicationIntegrationId = "sa-integration-2",
                sourceApplicationInstanceId = "sa-instance-2",
                name = EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                timestamp = odt(2024, 1, 1, 12, 3),
                args = mapOf("filename" to "keep-me.pdf"),
            )

        eventRepository.saveAllAndFlush(listOf(firstMatchingError, secondMatchingError, matchingInfo, otherError))

        listener.accept(instanceDeletedConsumerRecord(matchingHeaders))

        val matchingEventIds = listOf(firstMatchingError.id, secondMatchingError.id, matchingInfo.id)

        matchingEventIds.forEach { eventId ->
            assertThat(isScrubbed(eventId)).isTrue()
            assertThat(scrubbedAt(eventId)).isNotNull()
        }
        assertThat(errorArgValues(firstMatchingError.id)).containsExactly("", "")
        assertThat(errorArgValues(secondMatchingError.id)).containsExactly("")
        assertThat(isScrubbed(otherError.id)).isFalse()
        assertThat(scrubbedAt(otherError.id)).isNull()
        assertThat(errorArgValues(otherError.id)).containsExactly("keep-me.pdf")

        val scrubbedAtBeforeSecondConsume =
            matchingEventIds.associateWith { eventId ->
                requireNotNull(scrubbedAt(eventId))
            }

        Thread.sleep(20)
        listener.accept(instanceDeletedConsumerRecord(matchingHeaders))

        val scrubbedAtAfterSecondConsume =
            matchingEventIds.associateWith { eventId ->
                requireNotNull(scrubbedAt(eventId))
            }

        assertThat(scrubbedAtAfterSecondConsume).isEqualTo(scrubbedAtBeforeSecondConsume)
        assertThat(errorArgValues(firstMatchingError.id)).containsExactly("", "")
        assertThat(errorArgValues(secondMatchingError.id)).containsExactly("")
        assertThat(isScrubbed(otherError.id)).isFalse()
        assertThat(errorArgValues(otherError.id)).containsExactly("keep-me.pdf")
    }

    @Test
    fun `consumer is configured with dedicated group and seek-to-beginning assignment policy`() {
        assertThat(listenerConfiguration.groupIdSuffix).isEqualTo("instance-deleted-scrubber")
        assertThat(listenerConfiguration.isSeekingOffsetResetOnAssignment).isTrue()
        assertThat(topicNameParameters.eventName).isEqualTo(EventCategory.INSTANCE_DELETED.eventName)
    }

    private fun instanceDeletedConsumerRecord(headers: InstanceFlowHeaders): InstanceFlowConsumerRecord<Any> {
        return InstanceFlowConsumerRecord
            .builder<Any>()
            .instanceFlowHeaders(headers)
            .consumerRecord(ConsumerRecord("instance-deleted", 0, 0L, "key", Any()))
            .build()
    }

    private fun headers(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
    ): InstanceFlowHeaders {
        return InstanceFlowHeaders
            .builder()
            .sourceApplicationId(sourceApplicationId)
            .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
            .sourceApplicationInstanceId(sourceApplicationInstanceId)
            .correlationId(UUID.randomUUID())
            .integrationId(100L)
            .build()
    }

    private fun infoEvent(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        name: String,
        timestamp: OffsetDateTime,
    ): EventEntity {
        return event(
            sourceApplicationId = sourceApplicationId,
            sourceApplicationIntegrationId = sourceApplicationIntegrationId,
            sourceApplicationInstanceId = sourceApplicationInstanceId,
            name = name,
            timestamp = timestamp,
            type = EventType.INFO,
            errors = emptyList(),
        )
    }

    private fun errorEvent(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        name: String,
        timestamp: OffsetDateTime,
        args: Map<String, String>,
    ): EventEntity {
        return event(
            sourceApplicationId = sourceApplicationId,
            sourceApplicationIntegrationId = sourceApplicationIntegrationId,
            sourceApplicationInstanceId = sourceApplicationInstanceId,
            name = name,
            timestamp = timestamp,
            type = EventType.ERROR,
            errors =
                listOf(
                    ErrorEntity
                        .builder()
                        .errorCode("test-error")
                        .args(args)
                        .build(),
                ),
        )
    }

    private fun event(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        name: String,
        timestamp: OffsetDateTime,
        type: EventType,
        errors: List<ErrorEntity>,
    ): EventEntity {
        return EventEntity
            .builder()
            .instanceFlowHeaders(
                InstanceFlowHeadersEmbeddable
                    .builder()
                    .sourceApplicationId(sourceApplicationId)
                    .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                    .sourceApplicationInstanceId(sourceApplicationInstanceId)
                    .integrationId(100L)
                    .build(),
            ).name(name)
            .timestamp(timestamp)
            .type(type)
            .errors(errors)
            .build()
    }

    private fun isScrubbed(eventId: Long): Boolean {
        return jdbcTemplate.queryForObject(
            "SELECT is_scrubbed FROM event WHERE id = ?",
            { rs, _ -> rs.getBoolean("is_scrubbed") },
            eventId,
        )!!
    }

    private fun scrubbedAt(eventId: Long): Timestamp? {
        return jdbcTemplate.queryForObject(
            "SELECT scrubbed_at FROM event WHERE id = ?",
            { rs, _ -> rs.getTimestamp("scrubbed_at") },
            eventId,
        )
    }

    private fun errorArgValues(eventId: Long): List<String> {
        return jdbcTemplate.query(
            """
            SELECT ea."value"
            FROM error_args ea
            JOIN error er ON er.id = ea.error_id
            WHERE er.event_id = ?
            ORDER BY ea.map_key
            """.trimIndent(),
            { rs, _ -> rs.getString("value") },
            eventId,
        )
    }

    private fun odt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): OffsetDateTime = OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC)

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
