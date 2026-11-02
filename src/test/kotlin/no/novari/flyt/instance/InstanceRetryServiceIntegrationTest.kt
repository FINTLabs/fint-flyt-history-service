package no.novari.flyt.instance

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.history.Application
import no.novari.flyt.history.EventService
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.mapping.EventMappingService
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummariesFilterMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummaryMappingService
import no.novari.flyt.history.mapping.IntegrationStatisticsFilterMappingService
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.config.InstanceDatabaseConfiguration
import no.novari.flyt.instance.config.InstanceJpaConfiguration
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryEventProducerService
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeoutException

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = [
        "novari.flyt.instance-functionality.enabled=true",
        "fint.application-id=retry-history-app",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
        "spring.jpa.open-in-view=false",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(
    JpaAuditingTestConfig::class,
    InstanceDatabaseConfiguration::class,
    InstanceJpaConfiguration::class,
    InstanceRetryService::class,
    InstanceService::class,
    InstanceMappingService::class,
    EventService::class,
    InstanceInfoEventService::class,
    InstanceErrorEventService::class,
    InstanceFlowHeadersMappingService::class,
    InstanceRetryServiceIntegrationTest.FixedClock::class,
)
@MockitoBean(
    types = [
        EventMappingService::class,
        InstanceFlowSummariesFilterMappingService::class,
        InstanceFlowSummaryMappingService::class,
        IntegrationStatisticsFilterMappingService::class,
        EventCategorizationService::class,
    ],
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceRetryServiceIntegrationTest {
    @Autowired lateinit var retry: InstanceRetryService

    @Autowired lateinit var instances: InstanceService

    @Autowired lateinit var events: EventRepository

    @Autowired lateinit var info: InstanceInfoEventService

    @Autowired lateinit var headersMapper: InstanceFlowHeadersMappingService

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean lateinit var producer: InstanceRequestedForRetryEventProducerService

    @MockitoSpyBean lateinit var errors: InstanceErrorEventService

    @AfterEach
    fun cleanUp() {
        events.deleteAll()
        jdbc.execute("TRUNCATE mapping_test_instance.instance_object CASCADE")
    }

    @Test
    fun `retry fully materializes graph and latest headers before sending without a transaction`() {
        val (saved, headers) = registered()
        val instanceId = requireNotNull(saved.id)
        val older =
            headers
                .toBuilder()
                .fileIds(emptyList())
                .configurationId(9L)
                .build()
        info.registerEvent(older, EventCategory.INSTANCE_REGISTERED, NOW.minusSeconds(1), "old-app")
        doAnswer {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            assertThat(it.getArgument<InstanceObjectDto>(1)).usingRecursiveComparison().isEqualTo(saved)
            assertThat(it.getArgument<InstanceFlowHeaders>(0).fileIds).containsExactlyElementsOf(headers.fileIds)
            null
        }.whenever(producer).publish(any(), any())

        retry.retry(instanceId)
        retry.retry(instanceId)

        val sent = argumentCaptor<InstanceFlowHeaders>()
        verify(producer, times(2)).publish(sent.capture(), any())
        assertThat(
            sent.allValues.map { it.correlationId },
        ).doesNotHaveDuplicates().doesNotContain(headers.correlationId)
        sent.allValues.forEach {
            assertThat(it.toBuilder().correlationId(headers.correlationId).build()).isEqualTo(headers)
        }
        assertThat(instances.getById(instanceId)).usingRecursiveComparison().isEqualTo(saved)
        assertThat(events.count()).isEqualTo(2)
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM retry_test_history.instance_received_offset", Long::class.java)!!,
        ).isZero()
    }

    @Test
    fun `failed publication commits one local retry error with clock origin and request actor`() {
        val (saved, headers) = registered()
        val failure = TimeoutException("broker acknowledgement timed out")
        val actor = Actor.User(UUID.randomUUID())
        doThrow(failure).whenever(producer).publish(any(), any())
        assertThatThrownBy { ActorContext.withActor(actor) { retry.retry(saved.id!!) } }.isSameAs(failure)
        val sent = argumentCaptor<InstanceFlowHeaders>()
        verify(producer).publish(sent.capture(), any())
        TransactionTemplate(transactionManager).executeWithoutResult {
            val error = events.findAll().single { it.type == EventType.ERROR }
            assertThat(error.name).isEqualTo(EventCategory.INSTANCE_RETRY_REQUEST_ERROR.eventName)
            assertThat(error.timestamp!!.toInstant()).isEqualTo(NOW)
            assertThat(error.applicationId).isEqualTo("retry-history-app")
            assertThat(error.createdBy).isEqualTo(actor)
            assertThat(headersMapper.toInstanceFlowHeaders(error.instanceFlowHeaders!!)).isEqualTo(sent.firstValue)
            assertThat(error.errors.single().errorCode).isEqualTo(ErrorCode.GENERAL_SYSTEM_ERROR.getCode())
            assertThat(error.errors.single().args).isNullOrEmpty()
        }
        assertThat(instances.getById(saved.id!!)).usingRecursiveComparison().isEqualTo(saved)
        assertThat(sent.firstValue.correlationId).isNotEqualTo(headers.correlationId)
        assertThat(events.count()).isEqualTo(2)
    }

    @Test
    fun `failed history write does not replace original publication failure`() {
        val (saved, _) = registered()
        val failure = IllegalStateException("publication failed")
        val historyFailure = IllegalStateException("history failed")
        doThrow(failure).whenever(producer).publish(any(), any())
        doThrow(historyFailure).whenever(errors).registerError(any(), any<EventCategory>(), any(), any(), any())
        assertThatThrownBy { retry.retry(saved.id!!) }.isSameAs(failure)
        assertThat(failure.suppressed).containsExactly(historyFailure)
    }

    @Test
    fun `missing instance and missing headers produce no publication or fabricated history`() {
        assertThatThrownBy {
            retry.retry(
                Long.MAX_VALUE,
            )
        }.isInstanceOf(jakarta.persistence.EntityNotFoundException::class.java)
        val saved = instances.save(InstanceObjectDto(valuePerKey = mutableMapOf("value" to "root")))
        assertThatThrownBy { retry.retry(saved.id!!) }.isInstanceOf(NoInstanceFlowHeadersException::class.java)
        verifyNoInteractions(producer, errors)
        assertThat(events.count()).isZero()
    }

    @Test
    fun `retry refuses an outer transaction`() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThatThrownBy { retry.retry(123) }.isInstanceOf(IllegalTransactionStateException::class.java)
        }
        verifyNoInteractions(producer)
    }

    private fun registered(): Pair<InstanceObjectDto, InstanceFlowHeaders> {
        val fixture = outboxEvent()
        val graph =
            fixture.instance.copy(
                id = null,
                objectCollectionPerKey =
                    mutableMapOf(
                        "children" to listOf(InstanceObjectDto(valuePerKey = mutableMapOf("child" to "value"))),
                        "empty" to emptyList(),
                    ),
            )
        val saved = instances.save(graph)
        val headers =
            fixture.headers
                .toBuilder()
                .instanceId(saved.id)
                .build()
        info.registerEvent(headers, EventCategory.INSTANCE_REGISTERED, NOW, "registration-app")
        return saved to headers
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClock {
        @Bean fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }

    companion object {
        private val NOW = Instant.parse("2026-11-02T10:20:30Z")

        @JvmField @Container
        val postgres = PostgreSQLContainer("postgres:17").withInitScript("db/instance-fixture/init.sql")

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fint.database.url", postgres::getJdbcUrl)
            registry.add("fint.database.username", postgres::getUsername)
            registry.add("fint.database.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.schema") { "retry_test_history" }
            registry.add("novari.flyt.instance.database.schema") { "mapping_test_instance" }
        }
    }
}
