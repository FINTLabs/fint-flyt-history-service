package no.novari.flyt.instance

import no.novari.flyt.history.Application
import no.novari.flyt.instance.kafka.InstanceReceivedKafkaMetadataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@JdbcTest(properties = ["novari.flyt.instance-functionality.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(InstanceReceivedOffsetRepository::class, InstanceReceivedPositionService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class InstanceReceivedPositionIntegrationTest {
    @Autowired
    lateinit var positions: InstanceReceivedPositionService

    @Autowired
    lateinit var repository: InstanceReceivedOffsetRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    lateinit var metadataService: InstanceReceivedKafkaMetadataService

    @BeforeEach
    fun brokerMetadata() {
        whenever(metadataService.read()).thenAnswer {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            receivedMetadata()
        }
    }

    @AfterEach
    fun cleanUp() {
        jdbc.update("DELETE FROM instance_received_offset")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event", Long::class.java)!!).isZero()
    }

    @ParameterizedTest
    @CsvSource("0,0,0", "5,20,5", "5,20,20")
    fun `initialization accepts inclusive offset bounds and empty partitions`(
        start: Long,
        end: Long,
        offset: Long,
    ) {
        whenever(metadataService.read()).thenAnswer {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            receivedMetadata(start, end)
        }

        val initialized = positions.initialize(positionInitialization(offset))

        assertThat(initialized.nextOffset).isEqualTo(offset)
        assertThat(positions.getValidatedPosition(0)).isEqualTo(initialized)
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM instance_received_offset", Long::class.java)!!,
        ).isEqualTo(1)
    }

    @Test
    fun `a missing position is rejected without choosing a start offset`() {
        assertThrows<InstanceReceivedOffsetException> { positions.getValidatedPosition(0) }
        assertThat(repository.find(RECEIVED_TOPIC, 0)).isNull()
    }

    @Test
    fun `binding identity preserves the explicit legacy position`() {
        insert(InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, null, null))
        val request = positionInitialization().copy(expectedPosition = ExpectedInstanceReceivedPosition.Legacy(10))

        val bound = positions.initialize(request)

        assertThat(bound.nextOffset).isEqualTo(10)
        assertThat(bound.clusterId).isEqualTo(RECEIVED_CLUSTER)
        assertThat(bound.topicId).isEqualTo(RECEIVED_TOPIC_ID)
        assertThat(positions.getValidatedPosition(0)).isEqualTo(bound)
    }

    @Test
    fun `an identical initialization is idempotent without updating the row`() {
        val request = positionInitialization()
        val initialized = positions.initialize(request)
        val before = jdbc.queryForMap("SELECT * FROM instance_received_offset")

        assertThat(positions.initialize(request)).isEqualTo(initialized)
        assertThat(jdbc.queryForMap("SELECT * FROM instance_received_offset")).isEqualTo(before)
    }

    @Test
    fun `legacy binding is idempotent after identity was assigned`() {
        insert(InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, null, null))
        val request = positionInitialization().copy(expectedPosition = ExpectedInstanceReceivedPosition.Legacy(10))
        val initialized = positions.initialize(request)

        assertThat(positions.initialize(request)).isEqualTo(initialized)
    }

    @ParameterizedTest
    @MethodSource("invalidPositions")
    fun `invalid stored positions are rejected without being corrected`(position: InstanceReceivedPosition) {
        insert(position)
        whenever(metadataService.read()).thenReturn(receivedMetadata(5, 20))

        assertThrows<InstanceReceivedOffsetException> { positions.getValidatedPosition(position.partition) }
        assertThat(repository.find(position.topic, position.partition)).isEqualTo(position)
    }

    @Test
    fun `initialization rejects invalid broker identity partitions and offsets without writing`() {
        val base = positionInitialization()
        val invalid =
            listOf(
                base.copy(topic = "other-topic"),
                base.copy(clusterId = "other-cluster"),
                base.copy(topicId = UUID.randomUUID()),
                base.copy(topicId = UUID(0, 0)),
                base.copy(partition = 1),
                base.copy(nextOffset = -1),
                base.copy(nextOffset = 21),
            )
        invalid.forEach { request -> assertThrows<InstanceReceivedOffsetException> { positions.initialize(request) } }
        assertThat(repository.find(RECEIVED_TOPIC, 0)).isNull()
    }

    @Test
    fun `legacy binding refuses a missing row or a different expected offset`() {
        val request = positionInitialization().copy(expectedPosition = ExpectedInstanceReceivedPosition.Legacy(10))
        assertThrows<InstanceReceivedOffsetException> { positions.initialize(request) }
        insert(InstanceReceivedPosition(RECEIVED_TOPIC, 0, 11, null, null))

        assertThrows<InstanceReceivedOffsetException> { positions.initialize(request) }
        assertThrows<InstanceReceivedOffsetException> { positions.initialize(positionInitialization(11)) }
        assertThrows<InstanceReceivedOffsetException> {
            positions.initialize(
                request.copy(nextOffset = 12, expectedPosition = ExpectedInstanceReceivedPosition.Legacy(11)),
            )
        }
        assertThat(
            repository.find(RECEIVED_TOPIC, 0),
        ).isEqualTo(InstanceReceivedPosition(RECEIVED_TOPIC, 0, 11, null, null))
    }

    @Test
    fun `old initialization requests cannot overwrite registration progress`() {
        val request = positionInitialization()
        positions.initialize(request)
        TransactionTemplate(transactionManager).executeWithoutResult {
            repository.advance(
                InstanceReceivedMessageId(RECEIVED_TOPIC, 0, 10, RECEIVED_CLUSTER, RECEIVED_TOPIC_ID),
                10,
            )
        }

        assertThrows<InstanceReceivedOffsetException> { positions.initialize(request) }
        assertThat(positions.getValidatedPosition(0).nextOffset).isEqualTo(11)
    }

    @Test
    fun `initialization refuses an already initialized different generation`() {
        val original = InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, RECEIVED_CLUSTER, UUID.randomUUID())
        insert(original)

        assertThrows<InstanceReceivedOffsetException> { positions.initialize(positionInitialization()) }
        assertThat(repository.find(RECEIVED_TOPIC, 0)).isEqualTo(original)
    }

    @Test
    fun `an outer database transaction is rejected before any broker lookup`() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThrows<IllegalTransactionStateException> { positions.initialize(positionInitialization()) }
            assertThrows<IllegalTransactionStateException> { positions.getValidatedPosition(0) }
        }
        verifyNoInteractions(metadataService)
        assertThat(repository.find(RECEIVED_TOPIC, 0)).isNull()
    }

    @Test
    fun `repository initialization requires a transaction`() {
        assertThrows<IllegalTransactionStateException> { repository.initialize(positionInitialization()) }
    }

    @Test
    fun `concurrent identical initializations create one position`() {
        val results = concurrently(listOf(positionInitialization(), positionInitialization()))

        assertThat(results.all { it.isSuccess }).isTrue()
        assertThat(results.map { it.getOrThrow() }.distinct()).hasSize(1)
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM instance_received_offset", Long::class.java)!!,
        ).isEqualTo(1)
    }

    @Test
    fun `concurrent legacy bindings preserve the existing offset`() {
        insert(InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, null, null))
        val request = positionInitialization().copy(expectedPosition = ExpectedInstanceReceivedPosition.Legacy(10))

        val results = concurrently(listOf(request, request))

        assertThat(results.all { it.isSuccess }).isTrue()
        assertThat(results.map { it.getOrThrow() }.distinct()).hasSize(1)
        assertThat(positions.getValidatedPosition(0).nextOffset).isEqualTo(10)
    }

    @Test
    fun `concurrent conflicting initializations never overwrite the winning position`() {
        val results = concurrently(listOf(positionInitialization(10), positionInitialization(11)))

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(
            results.single { it.isFailure }.exceptionOrNull(),
        ).isInstanceOf(InstanceReceivedOffsetException::class.java)
        assertThat(positions.getValidatedPosition(0)).isEqualTo(results.single { it.isSuccess }.getOrThrow())
    }

    private fun concurrently(
        requests: List<InstanceReceivedPositionInitialization>,
    ): List<Result<InstanceReceivedPosition>> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks =
                requests.map { request ->
                    executor.submit<Result<InstanceReceivedPosition>> {
                        check(start.await(10, TimeUnit.SECONDS))
                        runCatching { positions.initialize(request) }
                    }
                }
            start.countDown()
            return tasks.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun insert(position: InstanceReceivedPosition) {
        jdbc.update(
            "INSERT INTO instance_received_offset (topic_name, partition_id, next_offset, cluster_id, topic_id) VALUES (?, ?, ?, ?, ?)",
            position.topic,
            position.partition,
            position.nextOffset,
            position.clusterId,
            position.topicId,
        )
    }

    companion object {
        @JvmField
        @Container
        val postgres = PostgreSQLContainer("postgres:17")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.schema") { "received_positions" }
            registry.add("spring.flyway.schemas") { "received_positions" }
            registry.add("spring.flyway.default-schema") { "received_positions" }
        }

        @JvmStatic
        fun invalidPositions(): List<InstanceReceivedPosition> =
            listOf(
                InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, null, null),
                InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, "other-cluster", RECEIVED_TOPIC_ID),
                InstanceReceivedPosition(RECEIVED_TOPIC, 0, 10, RECEIVED_CLUSTER, UUID.randomUUID()),
                InstanceReceivedPosition(RECEIVED_TOPIC, 0, 4, RECEIVED_CLUSTER, RECEIVED_TOPIC_ID),
                InstanceReceivedPosition(RECEIVED_TOPIC, 0, 21, RECEIVED_CLUSTER, RECEIVED_TOPIC_ID),
                InstanceReceivedPosition(RECEIVED_TOPIC, 1, 10, RECEIVED_CLUSTER, RECEIVED_TOPIC_ID),
            )
    }
}
