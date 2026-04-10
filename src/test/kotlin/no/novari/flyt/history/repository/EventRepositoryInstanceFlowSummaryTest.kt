package no.novari.flyt.history.repository

import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder
import no.novari.flyt.history.repository.filters.InstanceStorageStatusQueryFilter
import no.novari.flyt.history.repository.filters.TimeQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.ALL_EVENTS_AND_EXPECTED_SUMMARIES
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.ALL_STATUS_EVENT_NAMES
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.ALL_STORAGE_STATUS_EVENT_NAMES
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.DESTINATION_INSTANCE_ID_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.DESTINATION_INSTANCE_ID_2
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA1_1_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA1_1_2
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA2_2_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA2_3_3
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA3_4_4
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA_INSTANCE_ID_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA_INSTANCE_ID_4
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA_INTEGRATION_ID_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA_INTEGRATION_ID_2
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.SA_INTEGRATION_ID_3
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.STATUS_EVENT_NAME_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.STATUS_EVENT_NAME_3
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.STATUS_EVENT_NAME_5
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.STORAGE_STATUS_EVENT_NAME_1
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.STORAGE_STATUS_EVENT_NAME_2
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.UNUSED_LONG_ID
import no.novari.flyt.history.repository.utils.instanceflowsummary.Dataset.Companion.UNUSED_STRING_ID
import no.novari.flyt.history.repository.utils.instanceflowsummary.FilterPropertyTestCaseConfiguration
import no.novari.flyt.history.repository.utils.instanceflowsummary.InstanceFlowSummariesTestCase
import no.novari.flyt.history.repository.utils.instanceflowsummary.ParameterizedInstanceFlowTestCaseGenerator
import no.novari.flyt.history.repository.utils.instanceflowsummary.ParameterizedInstanceFlowTestCaseGenerator.Companion.createFilterPropertyTestCases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.stream.Stream

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventRepositoryInstanceFlowSummaryTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @BeforeEach
    fun setup() {
        if (isInitialized) {
            return
        }
        isInitialized = true
        eventRepository.saveAllAndFlush(
            ALL_EVENTS_AND_EXPECTED_SUMMARIES.flatMap { it.eventEntities },
        )
    }

    @ParameterizedTest
    @MethodSource("generateInstanceFlowSummariesTestCases")
    fun instanceFlowSummariesParameterized(
        filter: InstanceFlowSummariesQueryFilter,
        expectedInstanceFlowSummaries: List<InstanceFlowSummaryProjection>,
    ) {
        try {
            val instanceFlowSummaries =
                eventRepository.getInstanceFlowSummaries(
                    filter,
                    ALL_STATUS_EVENT_NAMES,
                    ALL_STORAGE_STATUS_EVENT_NAMES,
                    null,
                )

            assertThat(instanceFlowSummaries).isEqualTo(expectedInstanceFlowSummaries)
        } catch (e: Exception) {
            e.printStackTrace()
            generateSequence<Throwable>(e) { it.cause }.forEachIndexed { index, throwable ->
                println("CAUSE[$index]: ${throwable::class.qualifiedName}: ${throwable.message}")
            }
            throw e
        }
    }

    @ParameterizedTest
    @MethodSource("generateInstanceFlowSummariesTestCases")
    fun instanceFlowSummariesTotalCountParameterized(
        filter: InstanceFlowSummariesQueryFilter,
        expectedInstanceFlowSummaries: List<InstanceFlowSummaryProjection>,
    ) {
        try {
            val instanceFlowSummariesTotalCount =
                eventRepository.getInstanceFlowSummariesTotalCount(
                    filter,
                    ALL_STATUS_EVENT_NAMES,
                    ALL_STORAGE_STATUS_EVENT_NAMES,
                )

            assertThat(instanceFlowSummariesTotalCount).isEqualTo(expectedInstanceFlowSummaries.size.toLong())
        } catch (e: Exception) {
            e.printStackTrace()
            generateSequence<Throwable>(e) { it.cause }.forEachIndexed { index, throwable ->
                println("CAUSE[$index]: ${throwable::class.qualifiedName}: ${throwable.message}")
            }
            throw e
        }
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(8).toBytes())
                }

        private var isInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }

        @JvmStatic
        fun generateInstanceFlowSummariesTestCases(): Stream<Arguments> {
            val sourceApplicationIdTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<Long>,
                        ->
                        builder.sourceApplicationIds(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_LONG_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(listOf(1L), setOf(SA1_1_1, SA1_1_2)),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(1L, 2L), setOf(SA1_1_1, SA1_1_2, SA2_2_1, SA2_3_3)),
                    ),
                )

            val sourceApplicationIntegrationIdTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<String>,
                        ->
                        builder.sourceApplicationIntegrationIds(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_STRING_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(listOf(SA_INTEGRATION_ID_1), setOf(SA1_1_1, SA1_1_2)),
                        FilterPropertyTestCaseConfiguration(listOf(SA_INTEGRATION_ID_3), setOf(SA2_3_3)),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            listOf(SA_INTEGRATION_ID_1, SA_INTEGRATION_ID_2),
                            setOf(SA1_1_1, SA1_1_2, SA2_2_1),
                        ),
                    ),
                )

            val sourceApplicationInstanceIdTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<String>,
                        ->
                        builder.sourceApplicationInstanceIds(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_STRING_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(listOf(SA_INSTANCE_ID_1), setOf(SA1_1_1, SA2_2_1)),
                        FilterPropertyTestCaseConfiguration(listOf(SA_INSTANCE_ID_4), setOf(SA3_4_4)),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            listOf(SA_INSTANCE_ID_1, SA_INSTANCE_ID_4),
                            setOf(SA1_1_1, SA2_2_1, SA3_4_4),
                        ),
                    ),
                )

            val integrationIdTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<Long>,
                        ->
                        builder.integrationIds(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_LONG_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(listOf(101L), setOf(SA1_1_1, SA1_1_2)),
                        FilterPropertyTestCaseConfiguration(listOf(104L), setOf(SA3_4_4)),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(101L, 104L), setOf(SA1_1_1, SA1_1_2, SA3_4_4)),
                    ),
                )

            val timestampTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: TimeQueryFilter,
                        ->
                        builder.timeQueryFilter(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(
                                    OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 1000, ZoneOffset.UTC),
                                ).build(),
                            emptySet(),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMax(
                                    OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 1000, ZoneOffset.UTC),
                                ).build(),
                            emptySet(),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(
                                    OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 1000, ZoneOffset.UTC),
                                ).build(),
                            setOf(SA3_4_4),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMax(
                                    OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
                                ).build(),
                            setOf(SA2_2_1),
                        ),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                                .latestStatusTimestampMax(OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC))
                                .build(),
                            setOf(SA1_1_1, SA1_1_2, SA3_4_4),
                        ),
                    ),
                )

            val statusEventNamesTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<String>,
                        ->
                        builder.statusEventNames(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_STRING_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(listOf(STATUS_EVENT_NAME_5), setOf(SA3_4_4)),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            listOf(STATUS_EVENT_NAME_1, STATUS_EVENT_NAME_3),
                            setOf(SA1_1_1, SA2_3_3),
                        ),
                    ),
                )

            val storageStatusTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: InstanceStorageStatusQueryFilter,
                        ->
                        builder.instanceStorageStatusQueryFilter(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(listOf(STORAGE_STATUS_EVENT_NAME_1), null),
                            setOf(SA1_1_2),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(listOf(STORAGE_STATUS_EVENT_NAME_1), null),
                            setOf(SA1_1_2),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(null, true),
                            setOf(SA1_1_1, SA2_2_1),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(listOf(STORAGE_STATUS_EVENT_NAME_1), true),
                            setOf(SA1_1_1, SA1_1_2, SA2_2_1),
                        ),
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(
                                listOf(STORAGE_STATUS_EVENT_NAME_1, STORAGE_STATUS_EVENT_NAME_2),
                                null,
                            ),
                            setOf(SA1_1_2, SA2_3_3, SA3_4_4),
                        ),
                    ),
                    listOf(
                        FilterPropertyTestCaseConfiguration(
                            InstanceStorageStatusQueryFilter(listOf(STORAGE_STATUS_EVENT_NAME_2), null),
                            setOf(SA2_3_3, SA3_4_4),
                        ),
                    ),
                )

            val destinationIdTestCases =
                createFilterPropertyTestCases(
                    {
                            builder: InstanceFlowSummariesQueryFilterBuilder,
                            value: List<String>,
                        ->
                        builder.destinationIds(value)
                    },
                    listOf(
                        FilterPropertyTestCaseConfiguration(listOf(UNUSED_STRING_ID), emptySet()),
                        FilterPropertyTestCaseConfiguration(
                            listOf(DESTINATION_INSTANCE_ID_1, DESTINATION_INSTANCE_ID_2),
                            setOf(SA2_3_3, SA3_4_4),
                        ),
                    ),
                    emptyList(),
                )

            val instanceFlowSummariesTestCases: List<InstanceFlowSummariesTestCase> =
                ParameterizedInstanceFlowTestCaseGenerator.combineFilterPropertyTestCases(
                    listOf(
                        sourceApplicationIdTestCases,
                        sourceApplicationIntegrationIdTestCases,
                        sourceApplicationInstanceIdTestCases,
                        integrationIdTestCases,
                        timestampTestCases,
                        statusEventNamesTestCases,
                        storageStatusTestCases,
                        destinationIdTestCases,
                    ),
                )

            return instanceFlowSummariesTestCases
                .stream()
                .map { instanceFlowSummariesTestCase ->
                    Arguments.of(
                        instanceFlowSummariesTestCase.filter,
                        instanceFlowSummariesTestCase.expectedInstanceFlowSummaries,
                    )
                }
        }
    }
}
