package no.fintlabs.repository;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import no.fintlabs.repository.filters.TimeQueryFilter;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import no.fintlabs.repository.utils.BatchPersister;
import no.fintlabs.repository.utils.EventEntityGenerator;
import no.fintlabs.repository.utils.SequenceGenerationConfig;
import no.fintlabs.repository.utils.performance.PageSizePerformanceTestCase;
import no.fintlabs.repository.utils.performance.Timer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static no.fintlabs.repository.utils.EventSequence.*;
import static no.fintlabs.repository.utils.performance.DurationFormatter.formatDuration;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@Slf4j
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.properties.hibernate.jdbc.batch_size=500",
                "spring.jpa.properties.hibernate.order_inserts=true",
                "spring.jpa.properties.hibernate.order_updates=true"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ComponentScan(basePackages = {"no.fintlabs.repository.utils", "org.hibernate", "javax.persistence"})
public class EventRepositoryPerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16")
            .withUrlParam("reWriteBatchedInserts", "true")
            .withCreateContainerCmdModifier(createContainerCmd ->
                    Objects.requireNonNull(createContainerCmd.getHostConfig())
                            .withCpuCount(2L)
                            .withMemory(DataSize.ofGigabytes(8).toBytes())
            );

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl);
        registry.add("fint.database.username", postgreSQLContainer::getUsername);
        registry.add("fint.database.password", postgreSQLContainer::getPassword);
    }

    EventEntityGenerator eventEntityGenerator = new EventEntityGenerator(1);
    BatchPersister batchPersister = new BatchPersister(10000);

    @Autowired
    EventRepository eventRepository;

    EventCategorizationService eventCategorizationService = new EventCategorizationService();

    private static boolean isInitialized = false;

    private static OffsetDateTime createOffsetDateTime(int year, int month, int dayOfMonth, int hour) {
        return OffsetDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    @BeforeEach
    public void generateEvents() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        List<EventEntity> sourceApplication1Integration1Events = eventEntityGenerator.generateEvents(
                1L,
                "testIntegrationId1",
                10L,
                createOffsetDateTime(2024, 1, 6, 0),
                createOffsetDateTime(2024, 6, 17, 19),
                List.of(
                        new SequenceGenerationConfig(HAPPY_CASE, 25000),
                        new SequenceGenerationConfig(RECEIVAL_ERROR, 1000),
                        new SequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 200),
                        new SequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 2000),
                        new SequenceGenerationConfig(HAPPY_CASE, 3, "testSourceApplicationInstanceId1")
                )
        );
        List<EventEntity> sourceApplication1Integration2Events = eventEntityGenerator.generateEvents(
                1L,
                "testIntegrationId2",
                11L,
                createOffsetDateTime(2024, 3, 6, 18),
                createOffsetDateTime(2024, 9, 6, 12),
                List.of(
                        new SequenceGenerationConfig(HAPPY_CASE, 30000),
                        new SequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 200),
                        new SequenceGenerationConfig(DISPATCH_ERROR_RETRY_SUCCESS, 2000),
                        new SequenceGenerationConfig(MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS, 500),
                        new SequenceGenerationConfig(DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS, 150)
                )
        );
        List<EventEntity> sourceApplication2Integration4Events = eventEntityGenerator.generateEvents(
                2L,
                "testIntegrationId4",
                12L,
                createOffsetDateTime(2024, 1, 6, 18),
                createOffsetDateTime(2024, 12, 6, 12),
                List.of(
                        new SequenceGenerationConfig(HAPPY_CASE, 60000),
                        new SequenceGenerationConfig(RECEIVAL_ERROR, 2000)
                )
        );
        List<EventEntity> sourceApplication3Integrations = IntStream.rangeClosed(5, 150)
                .mapToObj(i -> "testIntegration" + i)
                .map(sourceApplicationIntegrationId -> eventEntityGenerator.generateEvents(
                        3L,
                        sourceApplicationIntegrationId,
                        13L,
                        createOffsetDateTime(2023, 1, 6, 18),
                        createOffsetDateTime(2025, 1, 6, 12),
                        List.of(
                                new SequenceGenerationConfig(HAPPY_CASE, 5000),
                                new SequenceGenerationConfig(RECEIVAL_ERROR, 10),
                                new SequenceGenerationConfig(MAPPING_ERROR_RETRY_SUCCESS, 20),
                                new SequenceGenerationConfig(DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS, 100)
                        )
                )).flatMap(Collection::stream)
                .toList();
        batchPersister.persistInBatches(
                eventRepository,
                Stream.of(
                                sourceApplication1Integration1Events,
                                sourceApplication1Integration2Events,
                                sourceApplication2Integration4Events,
                                sourceApplication3Integrations
                        )
                        .flatMap(Collection::stream)
                        .sorted(Comparator.comparing(EventEntity::getTimestamp))
                        .toList()
        );
    }

    @Order(0)
    @Test
    public void hasTestData() {
        assertThat(eventRepository.count()).isGreaterThan(0);
    }

    public static Stream<Arguments> instanceFlowTestCases() {
        List<PageSizePerformanceTestCase> pageSizePerformanceTestCases = List.of(
                new PageSizePerformanceTestCase(10, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(20, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(50, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(100, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(1000, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(10000, Duration.ofSeconds(10)),
                new PageSizePerformanceTestCase(100000, Duration.ofSeconds(20))
        );
        List<InstanceFlowSummariesQueryFilter> filters = List.of(
//                InstanceFlowSummariesQueryFilter
//                        .builder()
//                        .associatedEventNames(List.of(
//                                EventCategory.INSTANCE_MAPPING_ERROR.getEventName(),
//                                EventCategory.INSTANCE_REQUESTED_FOR_RETRY.getEventName(),
//                                EventCategory.INSTANCE_DISPATCHED.getEventName(),
//                                EventCategory.INSTANCE_MAPPING_ERROR.getEventName(),
//                                EventCategory.INSTANCE_DISPATCHING_ERROR.getEventName(),
//                                EventCategory.INSTANCE_RECEIVAL_ERROR.getEventName()
//                        ))
//                        .build(),
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .timeQueryFilter(TimeQueryFilter.EMPTY)
                        .build(),
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .timeQueryFilter(
                                TimeQueryFilter
                                        .builder()
                                        .latestStatusTimestampMin(createOffsetDateTime(2024, 3, 6, 18))
                                        .latestStatusTimestampMax(createOffsetDateTime(2024, 5, 6, 19))
                                        .build()
                        )
                        .build(),
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .statusEventNames(List.of(EventCategory.INSTANCE_DISPATCHED.getEventName()))
                        .storageStatusFilter(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(EventCategory.INSTANCE_DELETED.getEventName()),
                                        false
                                )
                        )
                        .associatedEventNames(List.of(
                                EventCategory.INSTANCE_MAPPING_ERROR.getEventName(),
                                EventCategory.INSTANCE_REQUESTED_FOR_RETRY.getEventName(),
                                EventCategory.INSTANCE_DISPATCHED.getEventName()
                        ))
                        .build()
        );
        List<Sort> sorts = List.of(
                Sort.unsorted(),
                Sort.by(Sort.Direction.DESC, "latestUpdate"),
                Sort.by(Sort.Direction.ASC, "latestUpdate")
        );

        return Lists.cartesianProduct(
                        filters,
                        sorts,
                        pageSizePerformanceTestCases
                ).stream()
                .map(List::toArray)
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("instanceFlowTestCases")
    public void instanceFlow(
            InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter,
            Sort sort,
            PageSizePerformanceTestCase pageSizePerformanceTestCase
    ) {
        Timer timer = new Timer();
        timer.start();
        Slice<InstanceFlowSummaryProjection> instanceFlowSummaries =
                eventRepository.getInstanceFlowSummaries(
                        instanceFlowSummariesQueryFilter,
                        eventCategorizationService.getAllInstanceStatusEventNames(),
                        eventCategorizationService.getAllInstanceStorageStatusEventNames(),
                        PageRequest.of(
                                0,
                                pageSizePerformanceTestCase.getPageSize(),
                                sort
                        )
                );
        Duration elapsedTime = timer.getElapsedTime();
        log.info("Elapsed time={}", formatDuration(elapsedTime));
        assertThat(instanceFlowSummaries.getNumberOfElements()).isPositive();
        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.getMaxElapsedTime());
    }

    @Test
    public void findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc() {
        Timer timer = new Timer();
        timer.start();
        List<String> archiveInstanceIdsOrderedByTimestamp =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        1L,
                        "testIntegrationId1",
                        "testSourceApplicationInstanceId1",
                        eventCategorizationService.getEventNamesPerInstanceStatus()
                );
        Duration elapsedTime = timer.getElapsedTime();
        assertThat(archiveInstanceIdsOrderedByTimestamp).hasSize(3);
        assertThat(elapsedTime).isLessThan(Duration.ofMillis(50));
    }

    @Test
    public void totalStatistics() {
        Timer timer = new Timer();
        timer.start();

        eventRepository.getTotalStatistics(
                List.of(1L, 2L),
                eventCategorizationService.getEventNamesPerInstanceStatus()
        );

        Duration elapsedTime = timer.getElapsedTime();
        assertThat(elapsedTime).isLessThan(Duration.ofSeconds(5));
    }

    public static Stream<PageSizePerformanceTestCase> integrationStatisticsTestCases() {
        return Stream.of(
                new PageSizePerformanceTestCase(10, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(20, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(50, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(100, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(1000, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(10000, Duration.ofSeconds(5)),
                new PageSizePerformanceTestCase(10000, Duration.ofSeconds(5))
        );
    }

    @ParameterizedTest
    @MethodSource("integrationStatisticsTestCases")
    public void integrationStatistics(PageSizePerformanceTestCase pageSizePerformanceTestCase) {
        Timer timer = new Timer();
        timer.start();
        Slice<IntegrationStatisticsProjection> integrationStatistics =
                eventRepository.getIntegrationStatistics(
                        IntegrationStatisticsQueryFilter
                                .builder()
                                .sourceApplicationIds(List.of(1L, 2L))
                                .build(),
                        eventCategorizationService.getEventNamesPerInstanceStatus(),
                        PageRequest.of(
                                0,
                                pageSizePerformanceTestCase.getPageSize()
                        )
                );
        Duration elapsedTime = timer.getElapsedTime();
        assertThat(integrationStatistics.getContent().size()).isEqualTo(3);
        assertThat(elapsedTime).isLessThan(pageSizePerformanceTestCase.getMaxElapsedTime());
    }

}
