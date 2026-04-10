package no.novari.flyt.history.repository

import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.InstanceStorageStatusQueryFilter
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import no.novari.flyt.history.repository.filters.TimeQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.history.repository.utils.BatchPersister
import no.novari.flyt.history.repository.utils.performance.DurationFormatter.formatDuration
import no.novari.flyt.history.repository.utils.performance.EventDatasetGenerator
import no.novari.flyt.history.repository.utils.performance.EventGenerationConfig
import no.novari.flyt.history.repository.utils.performance.EventSequence.DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS
import no.novari.flyt.history.repository.utils.performance.EventSequence.DISPATCH_ERROR_RETRY_SUCCESS
import no.novari.flyt.history.repository.utils.performance.EventSequence.HAPPY_CASE
import no.novari.flyt.history.repository.utils.performance.EventSequence.MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS
import no.novari.flyt.history.repository.utils.performance.EventSequence.MAPPING_ERROR_RETRY_SUCCESS
import no.novari.flyt.history.repository.utils.performance.EventSequence.RECEIVAL_ERROR
import no.novari.flyt.history.repository.utils.performance.EventSequenceGenerationConfig
import no.novari.flyt.history.repository.utils.performance.PageSizePerformanceTestCase
import no.novari.flyt.history.repository.utils.performance.Timer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.stream.IntStream
import java.util.stream.Stream

@Tag("performance")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DataJpaTest(
    showSql = false,
    properties = [
        "spring.jpa.properties.hibernate.jdbc.batch_size=500",
        "spring.jpa.properties.hibernate.order_inserts=true",
        "spring.jpa.properties.hibernate.order_updates=true",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ComponentScan(basePackages = ["no.novari.flyt.history.repository.utils", "org.hibernate", "javax.persistence"])
class EventRepositoryPerformanceTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    private lateinit var eventDatasetGenerator: EventDatasetGenerator
    private val eventCategorizationService = EventCategorizationService()

    @BeforeEach
    fun generateEvents() {
        log.info("PostgreSQL test container url: {}", postgreSQLContainer.jdbcUrl.split("?")[0])
        if (isInitialized) {
            return
        }
        isInitialized = true

        eventDatasetGenerator =
            EventDatasetGenerator(
                BatchPersister(eventRepository, 10000),
                1,
                1000000,
            )

        val numberOfEventsFactor = 10
        eventDatasetGenerator.generateAndPersistEvents(
            (
                listOf(
                    EventGenerationConfig
                        .builder()
                        .sourceApplicationId(1L)
                        .sourceApplicationIntegrationId("testSourceApplicationId1")
                        .integrationId(1L)
                        .minTimestamp(createOffsetDateTime(2024, 1, 6, 0))
                        .maxTimestamp(createOffsetDateTime(2024, 6, 17, 19))
                        .eventSequenceGenerationConfigs(
                            listOf(
                                EventSequenceGenerationConfig(HAPPY_CASE, 250 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(RECEIVAL_ERROR, 10 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 20 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(HAPPY_CASE, 3, "testSourceApplicationInstanceId1"),
                            ),
                        ).build(),
                    EventGenerationConfig
                        .builder()
                        .sourceApplicationId(1L)
                        .sourceApplicationIntegrationId("testSourceApplicationId2")
                        .integrationId(2L)
                        .minTimestamp(createOffsetDateTime(2024, 3, 6, 18))
                        .maxTimestamp(createOffsetDateTime(2024, 9, 6, 12))
                        .eventSequenceGenerationConfigs(
                            listOf(
                                EventSequenceGenerationConfig(HAPPY_CASE, 300 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 20 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(
                                    MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS,
                                    5 * numberOfEventsFactor,
                                ),
                                EventSequenceGenerationConfig(
                                    DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS,
                                    2 * numberOfEventsFactor,
                                ),
                            ),
                        ).build(),
                    EventGenerationConfig
                        .builder()
                        .sourceApplicationId(2L)
                        .sourceApplicationIntegrationId("testSourceApplicationId4")
                        .integrationId(12L)
                        .minTimestamp(createOffsetDateTime(2024, 1, 6, 18))
                        .maxTimestamp(createOffsetDateTime(2024, 12, 6, 12))
                        .eventSequenceGenerationConfigs(
                            listOf(
                                EventSequenceGenerationConfig(HAPPY_CASE, 600 * numberOfEventsFactor),
                                EventSequenceGenerationConfig(RECEIVAL_ERROR, 20 * numberOfEventsFactor),
                            ),
                        ).build(),
                ) +
                    IntStream
                        .rangeClosed(5, 150)
                        .mapToObj { i ->
                            EventGenerationConfig
                                .builder()
                                .sourceApplicationId(3L)
                                .sourceApplicationIntegrationId("testSourceApplication$i")
                                .integrationId(i.toLong())
                                .minTimestamp(createOffsetDateTime(2023, 1, 6, 18))
                                .maxTimestamp(createOffsetDateTime(2025, 1, 6, 12))
                                .eventSequenceGenerationConfigs(
                                    listOf(
                                        EventSequenceGenerationConfig(HAPPY_CASE, 50 * numberOfEventsFactor),
                                        EventSequenceGenerationConfig(RECEIVAL_ERROR, numberOfEventsFactor),
                                        EventSequenceGenerationConfig(
                                            MAPPING_ERROR_RETRY_SUCCESS,
                                            2 * numberOfEventsFactor,
                                        ),
                                        EventSequenceGenerationConfig(
                                            DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS,
                                            5 * numberOfEventsFactor,
                                        ),
                                    ),
                                ).build()
                        }.toList()
            ),
        )
    }

    @Order(0)
    @Test
    fun hasTestData() {
        assertThat(eventRepository.count()).isGreaterThan(0)
    }

    @ParameterizedTest
    @MethodSource("instanceFlowTestCases")
    fun instanceFlow(
        instanceFlowSummariesQueryFilter: InstanceFlowSummariesQueryFilter,
        pageSizePerformanceTestCase: PageSizePerformanceTestCase,
    ) {
        val timer = Timer.start()
        val instanceFlowSummaries: List<InstanceFlowSummaryProjection> =
            eventRepository.getInstanceFlowSummaries(
                instanceFlowSummariesQueryFilter,
                eventCategorizationService.allInstanceStatusEventNames,
                eventCategorizationService.allInstanceStorageStatusEventNames,
                pageSizePerformanceTestCase.requestedMaxSize,
            )
        val elapsedTime = timer.elapsedTime
        log.info("Elapsed time={}", formatDuration(elapsedTime))
        assertThat(instanceFlowSummaries).hasSize(pageSizePerformanceTestCase.expectedSize)
        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.maxElapsedTime)
    }

    @ParameterizedTest
    @MethodSource("instanceFlowTotalCountTestCases")
    fun instanceFlowTotalCount(instanceFlowSummariesQueryFilter: InstanceFlowSummariesQueryFilter) {
        val timer = Timer.start()
        eventRepository.getInstanceFlowSummariesTotalCount(
            instanceFlowSummariesQueryFilter,
            eventCategorizationService.allInstanceStatusEventNames,
            eventCategorizationService.allInstanceStorageStatusEventNames,
        )
        val elapsedTime = timer.elapsedTime
        log.info("Elapsed time={}", formatDuration(elapsedTime))
        assertThat(elapsedTime).isLessThan(Duration.ofSeconds(5))
    }

    @Test
    fun findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc() {
        val timer = Timer.start()
        val archiveInstanceIdsOrderedByTimestamp =
            eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1",
            )
        val elapsedTime = timer.elapsedTime
        assertThat(archiveInstanceIdsOrderedByTimestamp).hasSize(3)
        assertThat(elapsedTime).isLessThan(Duration.ofMillis(50))
    }

    @Test
    fun totalStatistics() {
        val timer = Timer.start()

        eventRepository.getTotalStatistics(
            listOf(1L, 2L),
            eventCategorizationService.eventNamesPerInstanceStatus,
        )

        val elapsedTime = timer.elapsedTime
        assertThat(elapsedTime).isLessThan(Duration.ofSeconds(5))
    }

    @ParameterizedTest
    @MethodSource("integrationStatisticsTestCases")
    fun integrationStatistics(pageSizePerformanceTestCase: PageSizePerformanceTestCase) {
        val timer = Timer.start()
        val integrationStatistics: Slice<IntegrationStatisticsProjection> =
            eventRepository.getIntegrationStatistics(
                IntegrationStatisticsQueryFilter
                    .builder()
                    .sourceApplicationIds(listOf(1L, 2L))
                    .build(),
                eventCategorizationService.eventNamesPerInstanceStatus,
                PageRequest.of(0, pageSizePerformanceTestCase.requestedMaxSize),
            )
        val elapsedTime = timer.elapsedTime
        assertThat(integrationStatistics.content.size).isEqualTo(3)
        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.maxElapsedTime)
    }

    data class InstanceFlowTestCase(
        val filter: InstanceFlowSummariesQueryFilter,
        val pageSizePerformance: PageSizePerformanceTestCase,
    )

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

        private val log = LoggerFactory.getLogger(EventRepositoryPerformanceTest::class.java)
        private var isInitialized = false

        private val INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID =
            InstanceFlowSummariesQueryFilter
                .builder()
                .sourceApplicationInstanceIds(listOf("testSourceApplicationInstanceId1"))
                .build()

        private val INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS =
            listOf(
                InstanceFlowSummariesQueryFilter.builder().build(),
                InstanceFlowSummariesQueryFilter.builder().sourceApplicationIds(listOf(1L)).build(),
                InstanceFlowSummariesQueryFilter.builder().sourceApplicationIds(listOf(1L, 2L)).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .sourceApplicationIntegrationIds(
                        listOf("testSourceApplicationId2"),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .sourceApplicationIntegrationIds(
                        listOf("testSourceApplicationId2", "testSourceApplicationId50"),
                    ).build(),
                InstanceFlowSummariesQueryFilter.builder().integrationIds(listOf(2L)).build(),
                InstanceFlowSummariesQueryFilter.builder().integrationIds(listOf(2L, 50L, 100L)).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .timeQueryFilter(
                        TimeQueryFilter
                            .builder()
                            .latestStatusTimestampMin(
                                createOffsetDateTime(2024, 3, 6, 18),
                            ).build(),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .timeQueryFilter(
                        TimeQueryFilter
                            .builder()
                            .latestStatusTimestampMax(
                                createOffsetDateTime(2024, 5, 6, 19),
                            ).build(),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .timeQueryFilter(
                        TimeQueryFilter
                            .builder()
                            .latestStatusTimestampMin(createOffsetDateTime(2024, 3, 6, 18))
                            .latestStatusTimestampMax(createOffsetDateTime(2024, 5, 6, 19))
                            .build(),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .statusEventNames(
                        listOf(EventCategory.INSTANCE_DISPATCHED.eventName),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .statusEventNames(
                        listOf(
                            EventCategory.INSTANCE_DISPATCHED.eventName,
                            EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                        ),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .instanceStorageStatusQueryFilter(
                        InstanceStorageStatusQueryFilter(
                            listOf(EventCategory.INSTANCE_DELETED.eventName),
                            false,
                        ),
                    ).build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .instanceStorageStatusQueryFilter(
                        InstanceStorageStatusQueryFilter(
                            listOf(),
                            true,
                        ),
                    ).build(),
            )

        private val INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS =
            listOf(
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .associatedEventNames(listOf(EventCategory.INSTANCE_MAPPING_ERROR.eventName))
                    .build(),
                InstanceFlowSummariesQueryFilter
                    .builder()
                    .associatedEventNames(
                        listOf(
                            EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                            EventCategory.INSTANCE_REQUESTED_FOR_RETRY.eventName,
                            EventCategory.INSTANCE_DISPATCHED.eventName,
                        ),
                    ).build(),
            )

        private val INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND =
            InstanceFlowSummariesQueryFilter
                .builder()
                .associatedEventNames(
                    listOf(
                        EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                        EventCategory.INSTANCE_REGISTRATION_ERROR.eventName,
                        EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                        EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                        EventCategory.INSTANCE_RETRY_REQUEST_ERROR.eventName,
                    ),
                ).build()

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }

        @JvmStatic
        fun instanceFlowTestCases(): Stream<Arguments> {
            val instanceFlowTestCases = mutableListOf<InstanceFlowTestCase>()

            instanceFlowTestCases +=
                InstanceFlowTestCase(
                    INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID,
                    PageSizePerformanceTestCase(10, 1, Duration.ofSeconds(2)),
                )

            instanceFlowTestCases +=
                createCartesianProductTestCases(
                    INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS,
                    listOf(
                        PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(2)),
                        PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(2)),
                        PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(3)),
                        PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(4)),
                        PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5)),
                    ),
                )

            instanceFlowTestCases +=
                createCartesianProductTestCases(
                    INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS,
                    listOf(
                        PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
                        PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5)),
                    ),
                )

            instanceFlowTestCases +=
                InstanceFlowTestCase(
                    INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND,
                    PageSizePerformanceTestCase(10, 0, Duration.ofSeconds(5)),
                )

            return instanceFlowTestCases
                .stream()
                .map { Arguments.of(it.filter, it.pageSizePerformance) }
        }

        @JvmStatic
        fun instanceFlowTotalCountTestCases(): Stream<Arguments> {
            val queryFilters =
                buildList {
                    add(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID)
                    addAll(INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS)
                    addAll(INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS)
                    add(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND)
                }

            return queryFilters.stream().map(Arguments::of)
        }

        @JvmStatic
        fun integrationStatisticsTestCases(): Stream<PageSizePerformanceTestCase> {
            return Stream.of(
                PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(5)),
                PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(5)),
                PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(5)),
                PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(5)),
                PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
                PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5)),
            )
        }

        private fun createOffsetDateTime(
            year: Int,
            month: Int,
            dayOfMonth: Int,
            hour: Int,
        ): OffsetDateTime = OffsetDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneOffset.UTC)

        private fun createCartesianProductTestCases(
            filters: List<InstanceFlowSummariesQueryFilter>,
            performanceTestCases: List<PageSizePerformanceTestCase>,
        ): List<InstanceFlowTestCase> {
            return filters.flatMap { filter ->
                performanceTestCases.map { performanceTestCase ->
                    InstanceFlowTestCase(filter, performanceTestCase)
                }
            }
        }
    }
}
