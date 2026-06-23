package no.novari.flyt.history

import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
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
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@Import(
    ScheduledErrorScrubService::class,
    EventScrubber::class,
    ScheduledErrorScrubServiceTest.FixedClockConfiguration::class,
    JpaAuditingTestConfig::class,
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(
    properties = [
        "novari.flyt.history-service.retention.time-to-keep-error-details-in-days=60",
        "novari.flyt.history-service.retention.scrub-batch-size=2",
    ],
)
class ScheduledErrorScrubServiceTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var scheduledErrorScrubService: ScheduledErrorScrubService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        eventRepository.deleteAll()
    }

    @Test
    fun `scrubs old events in batches and ignores already scrubbed and new events`() {
        val oldErrorEvent =
            errorEvent(
                timestamp = odt(2024, 1, 1, 12, 0),
                args = mapOf("filename" to "old-document.pdf"),
            )
        val oldInfoEvent =
            infoEvent(timestamp = odt(2024, 1, 2, 12, 0))
        val secondOldErrorEvent =
            errorEvent(
                timestamp = odt(2024, 1, 3, 12, 0),
                args = mapOf("reason" to "old-reason"),
            )
        val alreadyScrubbedAt = Timestamp.from(Instant.parse("2024-01-04T12:00:00Z"))
        val alreadyScrubbedEvent =
            errorEvent(
                timestamp = odt(2024, 1, 4, 12, 0),
                args = mapOf("reason" to "already-scrubbed"),
                isScrubbed = true,
                scrubbedAt = alreadyScrubbedAt.toInstant().atOffset(ZoneOffset.UTC),
            )
        val newErrorEvent =
            errorEvent(
                timestamp = odt(2024, 3, 1, 12, 0),
                args = mapOf("reason" to "new-reason"),
            )

        eventRepository.saveAllAndFlush(
            listOf(oldErrorEvent, oldInfoEvent, secondOldErrorEvent, alreadyScrubbedEvent, newErrorEvent),
        )

        val scrubbedCount = scheduledErrorScrubService.scrub()

        assertThat(scrubbedCount).isEqualTo(3)
        listOf(oldErrorEvent.id, oldInfoEvent.id, secondOldErrorEvent.id).forEach { eventId ->
            assertThat(isScrubbed(eventId)).isTrue()
            assertThat(scrubbedAt(eventId)).isNotNull()
        }
        assertThat(errorArgValues(oldErrorEvent.id)).containsExactly("")
        assertThat(errorArgValues(secondOldErrorEvent.id)).containsExactly("")

        assertThat(isScrubbed(alreadyScrubbedEvent.id)).isTrue()
        assertThat(scrubbedAt(alreadyScrubbedEvent.id)).isEqualTo(alreadyScrubbedAt)
        assertThat(errorArgValues(alreadyScrubbedEvent.id)).containsExactly("already-scrubbed")

        assertThat(isScrubbed(newErrorEvent.id)).isFalse()
        assertThat(scrubbedAt(newErrorEvent.id)).isNull()
        assertThat(errorArgValues(newErrorEvent.id)).containsExactly("new-reason")
    }

    private fun infoEvent(timestamp: OffsetDateTime): EventEntity {
        return event(
            timestamp = timestamp,
            type = EventType.INFO,
            errors = emptyList(),
        )
    }

    private fun errorEvent(
        timestamp: OffsetDateTime,
        args: Map<String, String>,
        isScrubbed: Boolean = false,
        scrubbedAt: OffsetDateTime? = null,
    ): EventEntity {
        return event(
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
            isScrubbed = isScrubbed,
            scrubbedAt = scrubbedAt,
        )
    }

    private fun event(
        timestamp: OffsetDateTime,
        type: EventType,
        errors: List<ErrorEntity>,
        isScrubbed: Boolean = false,
        scrubbedAt: OffsetDateTime? = null,
    ): EventEntity {
        return EventEntity
            .builder()
            .instanceFlowHeaders(
                InstanceFlowHeadersEmbeddable
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId("source-application-integration-id")
                    .sourceApplicationInstanceId("source-application-instance-id")
                    .integrationId(100L)
                    .build(),
            ).name(EventCategory.INSTANCE_RECEIVAL_ERROR.eventName)
            .timestamp(timestamp)
            .type(type)
            .errors(errors)
            .isScrubbed(isScrubbed)
            .scrubbedAt(scrubbedAt)
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
    ): OffsetDateTime = OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)

    @TestConfiguration
    class FixedClockConfiguration {
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2024-04-01T00:00:00Z"), ZoneOffset.UTC)
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
