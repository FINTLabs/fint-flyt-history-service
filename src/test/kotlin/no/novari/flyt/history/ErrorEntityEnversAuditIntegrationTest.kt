package no.novari.flyt.history

import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Verifiserer Variant D-flyten på ErrorEntity:
 * - INSERT skaper en revinfo-rad og en error_aud-rad (revtype=0)
 * - PII i error_args blir aldri speilet i en error_args_aud-tabell —
 *   @NotAudited på args hindrer at Envers genererer den
 * - Scrubbing oppdaterer last_modified_at/by på live-tabellen, men trigger
 *   IKKE en ny Envers-revisjon (Envers skriver bare revisjoner når auditerte
 *   felter endres; vi auditerer kun error_code, og scrubbing endrer kun args).
 *   "Når og av hvem scrubbing skjedde" leses derfor fra last_modified_*
 *   på live-tabellen — som er konsistent med kravanalysens behov.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ErrorScrubService::class, EventScrubber::class, JpaAuditingTestConfig::class, ClockConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = ["novari.flyt.history-service.retention.scrub-batch-size=10"])
class ErrorEntityEnversAuditIntegrationTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var errorScrubService: ErrorScrubService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Cleanup-rekkefølge: error_aud først (FK til revinfo), så revinfo, så event-tre
        jdbcTemplate.execute("DELETE FROM error_aud")
        jdbcTemplate.execute("DELETE FROM revinfo")
        eventRepository.deleteAll()
    }

    @Test
    fun `error_args_aud-tabellen finnes ikke — @NotAudited hindrer at PII speiles i historikk`() {
        val exists =
            jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'error_args_aud'
                )
                """.trimIndent(),
                Boolean::class.java,
            )
        assertThat(exists).isEqualTo(false)
    }

    @Test
    fun `INSERT av ErrorEntity skaper revinfo- og error_aud-rad`() {
        val event = saveEventWithError(args = mapOf("filename" to "doc.pdf"))
        val errorId = event.errors.single().id

        assertThat(countRevinfo()).isEqualTo(1)
        assertThat(errorAudRevtypes(errorId)).containsExactly(0)
    }

    @Test
    fun `error_aud lagrer kun auditerte kolonner — error_code men ikke args`() {
        val auditedColumns =
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'error_aud'",
                String::class.java,
            )
        assertThat(auditedColumns).containsExactlyInAnyOrder("id", "rev", "revtype", "error_code")
    }

    @Test
    fun `scrubbing oppdaterer last_modified på live-tabellen og tømmer args`() {
        val event =
            saveEventWithError(
                args = mapOf("filename" to "personlig-dokument.pdf", "reason" to "ugyldig-fnr"),
            )
        val errorId = event.errors.single().id

        val createdAt = lastModifiedAt(errorId)
        Thread.sleep(10)

        errorScrubService.scrubByInstanceFlowHeaders(headers(event))

        // PII er fjernet fra live-tabell.
        val liveArgs =
            jdbcTemplate.queryForList(
                """SELECT "value" FROM error_args WHERE error_id = ?""",
                String::class.java,
                errorId,
            )
        assertThat(liveArgs).allSatisfy { assertThat(it).isEmpty() }

        // last_modified_at er oppdatert — dokumenterer at scrubbing skjedde.
        assertThat(lastModifiedAt(errorId)).isAfter(createdAt)

        // Envers skriver IKKE en ny revisjon, fordi @Audited-feltet (error_code) er uendret.
        // INSERT-revisjonen står alene; "siste endring" leses fra live-tabellens last_modified_*.
        assertThat(errorAudRevtypes(errorId)).containsExactly(0)
    }

    private fun lastModifiedAt(errorId: Long): OffsetDateTime =
        jdbcTemplate.queryForObject(
            "SELECT last_modified_at FROM error WHERE id = ?",
            OffsetDateTime::class.java,
            errorId,
        )!!

    private fun saveEventWithError(args: Map<String, String>): EventEntity {
        val event =
            EventEntity(
                instanceFlowHeaders =
                    InstanceFlowHeadersEmbeddable
                        .builder()
                        .sourceApplicationId(1L)
                        .sourceApplicationIntegrationId("sa-integration-1")
                        .sourceApplicationInstanceId("sa-instance-1")
                        .integrationId(100L)
                        .build(),
                name = EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                type = EventType.ERROR,
                errors = mutableListOf(ErrorEntity(errorCode = "test-error", args = args)),
            )
        return eventRepository.saveAndFlush(event)
    }

    private fun headers(event: EventEntity): InstanceFlowHeaders {
        val embedded = requireNotNull(event.instanceFlowHeaders)
        return InstanceFlowHeaders
            .builder()
            .sourceApplicationId(requireNotNull(embedded.sourceApplicationId))
            .sourceApplicationIntegrationId(requireNotNull(embedded.sourceApplicationIntegrationId))
            .sourceApplicationInstanceId(requireNotNull(embedded.sourceApplicationInstanceId))
            .correlationId(UUID.randomUUID())
            .integrationId(requireNotNull(embedded.integrationId))
            .build()
    }

    private fun countRevinfo(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM revinfo", Int::class.java)!!

    private fun errorAudRevtypes(errorId: Long): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT revtype FROM error_aud WHERE id = ? ORDER BY rev",
            Int::class.java,
            errorId,
        )

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(2).toBytes())
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
