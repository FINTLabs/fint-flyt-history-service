package no.fintlabs.repository;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import no.fintlabs.repository.filters.TimeQueryFilter;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import no.fintlabs.repository.utils.instanceflowsummary.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static no.fintlabs.repository.utils.instanceflowsummary.Dataset.*;
import static no.fintlabs.repository.utils.instanceflowsummary.ParameterizedInstanceFlowTestCaseGenerator.createFilterPropertyTestCases;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EventRepositoryInstanceFlowSummaryTest {

    @Autowired
    EventRepository eventRepository;

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
        postgreSQLContainer.start();
        registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl);
        registry.add("fint.database.username", postgreSQLContainer::getUsername);
        registry.add("fint.database.password", postgreSQLContainer::getPassword);
    }

    private static boolean isInitialized = false;

    @BeforeEach
    public void setup() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        eventRepository.saveAllAndFlush(
                ALL_EVENTS_AND_EXPECTED_SUMMARIES.stream()
                        .map(EventEntitiesAndExpectedSummary::getEventEntities)
                        .flatMap(Collection::stream)
                        .toList()
        );
    }

    public static Stream<Arguments> generateInstanceFlowSummariesTestCases() {
        FilterPropertyTestCases sourceApplicationIdTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::sourceApplicationIds,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_LONG_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(1L),
                                Set.of(SA1_1_1, SA1_1_2)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(1L, 2L),
                                Set.of(SA1_1_1, SA1_1_2, SA2_2_1, SA2_3_3)
                        )
                )
        );
        FilterPropertyTestCases sourceApplicationIntegrationIdTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::sourceApplicationIntegrationIds,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_STRING_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INTEGRATION_ID_1),
                                Set.of(SA1_1_1, SA1_1_2)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INTEGRATION_ID_3),
                                Set.of(SA2_3_3)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INTEGRATION_ID_1, SA_INTEGRATION_ID_2),
                                Set.of(SA1_1_1, SA1_1_2, SA2_2_1)
                        )
                )
        );
        FilterPropertyTestCases sourceApplicationInstanceIdTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::sourceApplicationInstanceIds,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_STRING_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INSTANCE_ID_1),
                                Set.of(SA1_1_1, SA2_2_1)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INSTANCE_ID_4),
                                Set.of(SA3_4_4)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(SA_INSTANCE_ID_1, SA_INSTANCE_ID_4),
                                Set.of(SA1_1_1, SA2_2_1, SA3_4_4)
                        )
                )
        );
        FilterPropertyTestCases integrationIdTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::integrationIds,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_LONG_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(101L),
                                Set.of(SA1_1_1, SA1_1_2)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(104L),
                                Set.of(SA3_4_4)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(101L, 104L),
                                Set.of(SA1_1_1, SA1_1_2, SA3_4_4)
                        )
                )
        );
        FilterPropertyTestCases timestampTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::timeQueryFilter,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(
                                        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 1000, ZoneOffset.UTC)
                                )
                                .build(),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMax(
                                        OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 1000, ZoneOffset.UTC)
                                )
                                .build(),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(
                                        OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 1000, ZoneOffset.UTC)
                                )
                                .build(),
                                Set.of(SA3_4_4)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMax(
                                        OffsetDateTime.of(2024, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC)
                                )
                                .build(),
                                Set.of(SA2_2_1)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(TimeQueryFilter
                                .builder()
                                .latestStatusTimestampMin(
                                        OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)
                                )
                                .latestStatusTimestampMax(
                                        OffsetDateTime.of(2024, 1, 2, 8, 0, 0, 2000, ZoneOffset.UTC)
                                )
                                .build(),
                                Set.of(SA1_1_1, SA1_1_2, SA3_4_4)
                        )
                )
        );
        FilterPropertyTestCases statusEventNamesTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::statusEventNames,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_STRING_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(STATUS_EVENT_NAME_5),
                                Set.of(SA3_4_4)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(STATUS_EVENT_NAME_1, STATUS_EVENT_NAME_3),
                                Set.of(SA1_1_1, SA2_3_3)
                        )
                )
        );
        FilterPropertyTestCases storageStatusTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::instanceStorageStatusQueryFilter,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(STORAGE_STATUS_EVENT_NAME_1),
                                        null
                                ),
                                Set.of(SA1_1_2)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(STORAGE_STATUS_EVENT_NAME_1),
                                        null
                                ),
                                Set.of(SA1_1_2)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        null,
                                        true
                                ),
                                Set.of(SA1_1_1, SA2_2_1)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(STORAGE_STATUS_EVENT_NAME_1),
                                        true
                                ),
                                Set.of(SA1_1_1, SA1_1_2, SA2_2_1)
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(STORAGE_STATUS_EVENT_NAME_1, STORAGE_STATUS_EVENT_NAME_2),
                                        null
                                ),
                                Set.of(SA1_1_2, SA2_3_3, SA3_4_4)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                new InstanceStorageStatusQueryFilter(
                                        List.of(STORAGE_STATUS_EVENT_NAME_2),
                                        null
                                ),
                                Set.of(SA2_3_3, SA3_4_4)
                        )
                )
        );
        FilterPropertyTestCases destinationIdTestCases = createFilterPropertyTestCases(
                InstanceFlowSummariesQueryFilterBuilder::destinationIds,
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(UNUSED_STRING_ID),
                                Set.of()
                        ),
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(DESTINATION_INSTANCE_ID_1),
                                Set.of(SA2_3_3)
                        )
                ),
                List.of(
                        new FilterPropertyTestCaseConfiguration<>(
                                List.of(DESTINATION_INSTANCE_ID_1, DESTINATION_INSTANCE_ID_2),
                                Set.of(SA2_3_3, SA3_4_4)
                        )
                )
        );
        List<InstanceFlowSummariesTestCase> instanceFlowSummariesTestCases =
                ParameterizedInstanceFlowTestCaseGenerator.combineFilterPropertyTestCases(List.of(
                        sourceApplicationIdTestCases,
                        sourceApplicationIntegrationIdTestCases,
                        sourceApplicationInstanceIdTestCases,
                        integrationIdTestCases,
                        timestampTestCases,
                        statusEventNamesTestCases,
                        storageStatusTestCases,
                        destinationIdTestCases
                ));
        return instanceFlowSummariesTestCases
                .stream()
                .map(instanceFlowSummariesTestCase -> Arguments.of(
                        instanceFlowSummariesTestCase.getFilter(),
                        instanceFlowSummariesTestCase.getExpectedInstanceFlowSummaries()
                ));
    }

    @ParameterizedTest
    @MethodSource("generateInstanceFlowSummariesTestCases")
    public void instanceFlowSummariesParameterized(
            InstanceFlowSummariesQueryFilter filter,
            List<InstanceFlowSummaryProjection> expectedInstanceFlowSummaries
    ) {
        List<InstanceFlowSummaryProjection> instanceFlowSummaries = eventRepository.getInstanceFlowSummaries(
                filter,
                ALL_STATUS_EVENT_NAMES,
                ALL_STORAGE_STATUS_EVENT_NAMES,
                null
        );
        assertThat(instanceFlowSummaries).isEqualTo(expectedInstanceFlowSummaries);
    }
}
