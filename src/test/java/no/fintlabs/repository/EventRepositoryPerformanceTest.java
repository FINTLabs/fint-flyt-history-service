//package no.fintlabs.repository;
//
//import lombok.Builder;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import no.fintlabs.model.event.EventCategorizationService;
//import no.fintlabs.model.event.EventCategory;
//import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
//import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
//import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
//import no.fintlabs.repository.filters.TimeQueryFilter;
//import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
//import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
//import no.fintlabs.repository.utils.BatchPersister;
//import no.fintlabs.repository.utils.performance.*;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.MethodSource;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Slice;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.util.unit.DataSize;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.time.Duration;
//import java.time.OffsetDateTime;
//import java.time.ZoneOffset;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//import java.util.stream.IntStream;
//import java.util.stream.Stream;
//
//import static no.fintlabs.repository.utils.performance.DurationFormatter.formatDuration;
//import static no.fintlabs.repository.utils.performance.EventSequence.*;
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Tag("performance")
//@Slf4j
//@Testcontainers
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//@DataJpaTest(
//        showSql = false,
//        properties = {
//                "spring.jpa.properties.hibernate.jdbc.batch_size=500",
//                "spring.jpa.properties.hibernate.order_inserts=true",
//                "spring.jpa.properties.hibernate.order_updates=true"
//        })
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//@ComponentScan(basePackages = {"no.fintlabs.repository.utils", "org.hibernate", "javax.persistence"})
//public class EventRepositoryPerformanceTest {
//
//    @Container
//    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16")
//            .withUrlParam("reWriteBatchedInserts", "true")
//            .withCreateContainerCmdModifier(createContainerCmd ->
//                    Objects.requireNonNull(createContainerCmd.getHostConfig())
//                            .withCpuCount(2L)
//                            .withMemory(DataSize.ofGigabytes(8).toBytes())
//            );
//
//    @DynamicPropertySource
//    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
//        registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl);
//        registry.add("fint.database.username", postgreSQLContainer::getUsername);
//        registry.add("fint.database.password", postgreSQLContainer::getPassword);
//    }
//
//    EventDatasetGenerator eventDatasetGenerator;
//
//    @Autowired
//    EventRepository eventRepository;
//
//    EventCategorizationService eventCategorizationService = new EventCategorizationService();
//
//    private static boolean isInitialized = false;
//
//    private static OffsetDateTime createOffsetDateTime(int year, int month, int dayOfMonth, int hour) {
//        return OffsetDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneOffset.UTC);
//    }
//
//    @BeforeEach
//    public void generateEvents() {
//        log.info("PostgreSQL test container url: {}", postgreSQLContainer.getJdbcUrl().split("\\?")[0]);
//        if (isInitialized) {
//            return;
//        }
//        isInitialized = true;
//        eventDatasetGenerator = new EventDatasetGenerator(
//                new BatchPersister<>(eventRepository, 10000),
//                1,
//                1000000
//        );
//        int numberOfEventsFactor = 10;
//        eventDatasetGenerator.generateAndPersistEvents(Stream.concat(
//                Stream.of(
//                        EventGenerationConfig
//                                .builder()
//                                .sourceApplicationId(1L)
//                                .sourceApplicationIntegrationId("testSourceApplicationId1")
//                                .integrationId(1L)
//                                .minTimestamp(createOffsetDateTime(2024, 1, 6, 0))
//                                .maxTimestamp(createOffsetDateTime(2024, 6, 17, 19))
//                                .eventSequenceGenerationConfigs(List.of(
//                                        new EventSequenceGenerationConfig(HAPPY_CASE, 250 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(RECEIVAL_ERROR, 10 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 20 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(HAPPY_CASE, 3, "testSourceApplicationInstanceId1")
//                                ))
//                                .build(),
//                        EventGenerationConfig
//                                .builder()
//                                .sourceApplicationId(1L)
//                                .sourceApplicationIntegrationId("testSourceApplicationId2")
//                                .integrationId(2L)
//                                .minTimestamp(createOffsetDateTime(2024, 3, 6, 18))
//                                .maxTimestamp(createOffsetDateTime(2024, 9, 6, 12))
//                                .eventSequenceGenerationConfigs(List.of(
//                                        new EventSequenceGenerationConfig(HAPPY_CASE, 300 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 20 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS, 5 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor)
//                                ))
//                                .build(),
//                        EventGenerationConfig
//                                .builder()
//                                .sourceApplicationId(2L)
//                                .sourceApplicationIntegrationId("testSourceApplicationId4")
//                                .integrationId(12L)
//                                .minTimestamp(createOffsetDateTime(2024, 1, 6, 18))
//                                .maxTimestamp(createOffsetDateTime(2024, 12, 6, 12))
//                                .eventSequenceGenerationConfigs(List.of(
//                                        new EventSequenceGenerationConfig(HAPPY_CASE, 600 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(RECEIVAL_ERROR, 20 * numberOfEventsFactor)
//                                ))
//                                .build()
//                ),
//                IntStream.rangeClosed(5, 150)
//                        .mapToObj(i -> EventGenerationConfig
//                                .builder()
//                                .sourceApplicationId(3L)
//                                .sourceApplicationIntegrationId("testSourceApplication" + i)
//                                .integrationId(i)
//                                .minTimestamp(createOffsetDateTime(2023, 1, 6, 18))
//                                .maxTimestamp(createOffsetDateTime(2025, 1, 6, 12))
//                                .eventSequenceGenerationConfigs(List.of(
//                                        new EventSequenceGenerationConfig(HAPPY_CASE, 50 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(RECEIVAL_ERROR, numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 2 * numberOfEventsFactor),
//                                        new EventSequenceGenerationConfig(DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS, 5 * numberOfEventsFactor)
//                                ))
//                                .build()
//                        )
//        ).toList());
//    }
//
//    @Order(0)
//    @Test
//    public void hasTestData() {
//        assertThat(eventRepository.count()).isGreaterThan(0);
//    }
//
//    @Getter
//    @Builder
//    private static class InstanceFlowTestCase {
//        private final InstanceFlowSummariesQueryFilter filter;
//        private final PageSizePerformanceTestCase pageSizePerformance;
//    }
//
//    private static final InstanceFlowSummariesQueryFilter INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID =
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .sourceApplicationInstanceIds(List.of("testSourceApplicationInstanceId1"))
//                    .build();
//
//    private static final List<InstanceFlowSummariesQueryFilter> INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS = List.of(
//            InstanceFlowSummariesQueryFilter.builder().build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .sourceApplicationIds(List.of(1L))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .sourceApplicationIds(List.of(1L, 2L))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .sourceApplicationIntegrationIds(List.of("testSourceApplicationId2"))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .sourceApplicationIntegrationIds(List.of("testSourceApplicationId2", "testSourceApplicationId50"))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .integrationIds(List.of(2L))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .integrationIds(List.of(2L, 50L, 100L))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .timeQueryFilter(
//                            TimeQueryFilter
//                                    .builder()
//                                    .latestStatusTimestampMin(createOffsetDateTime(2024, 3, 6, 18))
//                                    .build()
//                    )
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .timeQueryFilter(
//                            TimeQueryFilter
//                                    .builder()
//                                    .latestStatusTimestampMax(createOffsetDateTime(2024, 5, 6, 19))
//                                    .build()
//                    )
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .timeQueryFilter(
//                            TimeQueryFilter
//                                    .builder()
//                                    .latestStatusTimestampMin(createOffsetDateTime(2024, 3, 6, 18))
//                                    .latestStatusTimestampMax(createOffsetDateTime(2024, 5, 6, 19))
//                                    .build()
//                    )
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .statusEventNames(List.of(
//                            EventCategory.INSTANCE_DISPATCHED.getEventName()
//                    ))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .statusEventNames(List.of(
//                            EventCategory.INSTANCE_DISPATCHED.getEventName(),
//                            EventCategory.INSTANCE_MAPPING_ERROR.getEventName()
//                    ))
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .instanceStorageStatusQueryFilter(
//                            new InstanceStorageStatusQueryFilter(
//                                    List.of(EventCategory.INSTANCE_DELETED.getEventName()),
//                                    false
//                            )
//                    )
//                    .build(),
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .instanceStorageStatusQueryFilter(
//                            new InstanceStorageStatusQueryFilter(
//                                    List.of(),
//                                    true)
//                    ).build()
//    );
//
//    private static final List<InstanceFlowSummariesQueryFilter> INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS =
//            List.of(
//                    InstanceFlowSummariesQueryFilter
//                            .builder()
//                            .associatedEventNames(List.of(
//                                    EventCategory.INSTANCE_MAPPING_ERROR.getEventName()
//                            ))
//                            .build(),
//                    InstanceFlowSummariesQueryFilter
//                            .builder()
//                            .associatedEventNames(List.of(
//                                    EventCategory.INSTANCE_MAPPING_ERROR.getEventName(),
//                                    EventCategory.INSTANCE_REQUESTED_FOR_RETRY.getEventName(),
//                                    EventCategory.INSTANCE_DISPATCHED.getEventName()
//                            ))
//                            .build()
//            );
//
//    private static final InstanceFlowSummariesQueryFilter INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND =
//            InstanceFlowSummariesQueryFilter
//                    .builder()
//                    .associatedEventNames(List.of(
//                            EventCategory.INSTANCE_RECEIVAL_ERROR.getEventName(),
//                            EventCategory.INSTANCE_REGISTRATION_ERROR.getEventName(),
//                            EventCategory.INSTANCE_MAPPING_ERROR.getEventName(),
//                            EventCategory.INSTANCE_RECEIVAL_ERROR.getEventName(),
//                            EventCategory.INSTANCE_RETRY_REQUEST_ERROR.getEventName()
//                    ))
//                    .build();
//
//    public static Stream<Arguments> instanceFlowTestCases() {
//        List<InstanceFlowTestCase> instanceFlowTestCases = new ArrayList<>();
//
//        instanceFlowTestCases.add(
//                InstanceFlowTestCase
//                        .builder()
//                        .filter(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID)
//                        .pageSizePerformance(new PageSizePerformanceTestCase(10, 1, Duration.ofSeconds(2)))
//                        .build()
//        );
//
//        instanceFlowTestCases.addAll(createCartesianProductTestCases(
//                INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS,
//                List.of(
//                        new PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(2)),
//                        new PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(2)),
//                        new PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(3)),
//                        new PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(4)),
//                        new PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5))
//                )
//        ));
//
//        instanceFlowTestCases.addAll(createCartesianProductTestCases(
//                INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS,
//                List.of(
//                        new PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
//                        new PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5))
//                )
//        ));
//
//        instanceFlowTestCases.add(InstanceFlowTestCase
//                .builder()
//                .filter(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND)
//                .pageSizePerformance(
//                        new PageSizePerformanceTestCase(10, 0, Duration.ofSeconds(5))
//                )
//                .build());
//
//        return instanceFlowTestCases
//                .stream()
//                .map(instanceFlowTestCase -> Arguments.of(
//                        instanceFlowTestCase.filter,
//                        instanceFlowTestCase.pageSizePerformance
//                ));
//    }
//
//    private static List<InstanceFlowTestCase> createCartesianProductTestCases(
//            List<InstanceFlowSummariesQueryFilter> filters,
//            List<PageSizePerformanceTestCase> performanceTestCases
//    ) {
//        return filters.stream()
//                .flatMap(filter -> performanceTestCases
//                        .stream()
//                        .map(performanceTestCase -> InstanceFlowTestCase
//                                .builder()
//                                .filter(filter)
//                                .pageSizePerformance(performanceTestCase)
//                                .build()
//                        )
//                ).toList();
//    }
//
//    @ParameterizedTest
//    @MethodSource("instanceFlowTestCases")
//    public void instanceFlow(
//            InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter,
//            PageSizePerformanceTestCase pageSizePerformanceTestCase
//    ) {
//        Timer timer = Timer.start();
//        List<InstanceFlowSummaryProjection> instanceFlowSummaries =
//                eventRepository.getInstanceFlowSummaries(
//                        instanceFlowSummariesQueryFilter,
//                        eventCategorizationService.getAllInstanceStatusEventNames(),
//                        eventCategorizationService.getAllInstanceStorageStatusEventNames(),
//                        pageSizePerformanceTestCase.getRequestedMaxSize()
//                );
//        Duration elapsedTime = timer.getElapsedTime();
//        log.info("Elapsed time={}", formatDuration(elapsedTime));
//        assertThat(instanceFlowSummaries).hasSize(pageSizePerformanceTestCase.getExpectedSize());
//        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.getMaxElapsedTime());
//    }
//
//    public static Stream<Arguments> instanceFlowTotalCountTestCases() {
//        List<InstanceFlowSummariesQueryFilter> queryFilters = new ArrayList<>();
//        queryFilters.add(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_SOURCE_APPLICATION_INSTANCE_ID);
//        queryFilters.addAll(INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS);
//        queryFilters.addAll(INSTANCE_FLOW_SUMMARIES_QUERY_FILTERS_ASSOCIATED_EVENTS);
//        queryFilters.add(INSTANCE_FLOW_SUMMARIES_QUERY_FILTER_ASSOCIATED_EVENTS_SINGLE_FIND);
//        return queryFilters
//                .stream()
//                .map(Arguments::of);
//    }
//
//    @ParameterizedTest
//    @MethodSource("instanceFlowTotalCountTestCases")
//    public void instanceFlowTotalCount(
//            InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter
//    ) {
//        Timer timer = Timer.start();
//        eventRepository.getInstanceFlowSummariesTotalCount(
//                instanceFlowSummariesQueryFilter,
//                eventCategorizationService.getAllInstanceStatusEventNames(),
//                eventCategorizationService.getAllInstanceStorageStatusEventNames()
//        );
//        Duration elapsedTime = timer.getElapsedTime();
//        log.info("Elapsed time={}", formatDuration(elapsedTime));
//        assertThat(elapsedTime).isLessThan(Duration.ofSeconds(5));
//    }
//
//    @Test
//    public void findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc() {
//        Timer timer = Timer.start();
//        List<String> archiveInstanceIdsOrderedByTimestamp =
//                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
//                        1L,
//                        "testSourceApplicationId1",
//                        "testSourceApplicationInstanceId1",
//                        eventCategorizationService.getEventNamesPerInstanceStatus()
//                );
//        Duration elapsedTime = timer.getElapsedTime();
//        assertThat(archiveInstanceIdsOrderedByTimestamp).hasSize(3);
//        assertThat(elapsedTime).isLessThan(Duration.ofMillis(50));
//    }
//
//    @Test
//    public void totalStatistics() {
//        Timer timer = Timer.start();
//
//        eventRepository.getTotalStatistics(
//                List.of(1L, 2L),
//                eventCategorizationService.getEventNamesPerInstanceStatus()
//        );
//
//        Duration elapsedTime = timer.getElapsedTime();
//        assertThat(elapsedTime).isLessThan(Duration.ofSeconds(5));
//    }
//
//    public static Stream<PageSizePerformanceTestCase> integrationStatisticsTestCases() {
//        return Stream.of(
//                new PageSizePerformanceTestCase(10, 10, Duration.ofSeconds(5)),
//                new PageSizePerformanceTestCase(20, 20, Duration.ofSeconds(5)),
//                new PageSizePerformanceTestCase(50, 50, Duration.ofSeconds(5)),
//                new PageSizePerformanceTestCase(100, 100, Duration.ofSeconds(5)),
//                new PageSizePerformanceTestCase(500, 500, Duration.ofSeconds(5)),
//                new PageSizePerformanceTestCase(1000, 1000, Duration.ofSeconds(5))
//        );
//    }
//
//    @ParameterizedTest
//    @MethodSource("integrationStatisticsTestCases")
//    public void integrationStatistics(PageSizePerformanceTestCase pageSizePerformanceTestCase) {
//        Timer timer = Timer.start();
//        Slice<IntegrationStatisticsProjection> integrationStatistics =
//                eventRepository.getIntegrationStatistics(
//                        IntegrationStatisticsQueryFilter
//                                .builder()
//                                .sourceApplicationIds(List.of(1L, 2L))
//                                .build(),
//                        eventCategorizationService.getEventNamesPerInstanceStatus(),
//                        PageRequest.of(
//                                0,
//                                pageSizePerformanceTestCase.getRequestedMaxSize()
//                        )
//                );
//        Duration elapsedTime = timer.getElapsedTime();
//        assertThat(integrationStatistics.getContent().size()).isEqualTo(3);
//        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.getMaxElapsedTime());
//    }
//
//}
