//package no.fintlabs.repository;
//
//import lombok.Builder;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import no.fintlabs.model.SourceApplicationAggregateInstanceId;
//import no.fintlabs.model.event.EventCategorizationService;
//import no.fintlabs.model.event.EventCategory;
//import no.fintlabs.model.event.EventType;
//import no.fintlabs.repository.entities.EventEntity;
//import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
//import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
//import no.fintlabs.repository.projections.InstanceStatisticsProjection;
//import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.data.domain.Pageable;
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
//import java.time.OffsetDateTime;
//import java.time.ZoneOffset;
//import java.util.Comparator;
//import java.util.List;
//import java.util.Objects;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Slf4j
//@Testcontainers
//@DataJpaTest(showSql = false)
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//public class EventRepositoryTest {
//
//    @Autowired
//    EventRepository eventRepository;
//
//    EventCategorizationService eventCategorizationService = new EventCategorizationService();
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
//        postgreSQLContainer.start();
//        registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl);
//        registry.add("fint.database.username", postgreSQLContainer::getUsername);
//        registry.add("fint.database.password", postgreSQLContainer::getPassword);
//    }
//
//    @Getter
//    @Builder
//    private static class TestSourceApplicationAggregateInstanceId implements SourceApplicationAggregateInstanceId {
//        private Long sourceApplicationId;
//        private String sourceApplicationIntegrationId;
//        private String sourceApplicationInstanceId;
//    }
//
//    @BeforeEach
//    public void setup() {
//        eventRepository.deleteAll();
//    }
//
//    @Nested
//    class findLatestStatusEventBySourceApplicationAggregateInstanceId {
//
//        @Test
//        public void givenNoEvents_whenFindLatestStatusEventBySourceApplicationAggregateInstanceId_thenReturnEmpty() {
//            Optional<EventEntity> latestStatusEventBySourceApplicationAggregateInstanceId =
//                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
//                            TestSourceApplicationAggregateInstanceId
//                                    .builder()
//                                    .sourceApplicationId(1L)
//                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                    .build(),
//                            eventCategorizationService.getAllInstanceStatusEventNames()
//                    );
//
//            assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEmpty();
//        }
//
//        @Test
//        public void givenNoEventsWithMatchingSourceApplicationAggregateId_whenFindLatestStatusEventBySourceApplicationAggregateInstanceId_thenReturnEmpty() {
//            eventRepository.save(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name("testName1")
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            );
//
//            Optional<EventEntity> latestStatusEventBySourceApplicationAggregateInstanceId =
//                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
//                            TestSourceApplicationAggregateInstanceId
//                                    .builder()
//                                    .sourceApplicationId(1L)
//                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                    .build(),
//                            eventCategorizationService.getAllInstanceStatusEventNames()
//                    );
//
//            assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEmpty();
//        }
//
//        @Test
//        public void givenEventsWithMatchingSourceApplicationAggregateInstanceId_whenCalled_thenReturnLatestStatusEvent() {
//            eventRepository.saveAll(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId0")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 1, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//
//            Optional<EventEntity> returnedOptionalEvent =
//                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
//                            TestSourceApplicationAggregateInstanceId
//                                    .builder()
//                                    .sourceApplicationId(1L)
//                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                    .build(),
//                            eventCategorizationService.getAllInstanceStatusEventNames()
//                    );
//
//            assertThat(returnedOptionalEvent).isPresent();
//            EventEntity returnedEvent = returnedOptionalEvent.get();
//            assertThat(returnedEvent)
//                    .usingRecursiveComparison()
//                    .ignoringFields("id")
//                    .withEqualsForType(
//                            OffsetDateTime::isEqual,
//                            OffsetDateTime.class
//                    )
//                    .isEqualTo(
//                            EventEntity.builder()
//                                    .instanceFlowHeaders(
//                                            InstanceFlowHeadersEmbeddable.builder()
//                                                    .sourceApplicationId(1L)
//                                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                                    .archiveInstanceId("testArchiveInstanceId1")
//                                                    .build()
//                                    )
//                                    .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                                    .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 1, 0, 0, ZoneOffset.UTC))
//                                    .type(EventType.INFO)
//                                    .build()
//                    );
//        }
//    }
//
//
//    @Nested
//    class findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc {
//
//        @Test
//        public void givenFullAggregateIdAndMatchingEventsShouldReturnArchiveInstanceIdsFromEvents() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .archiveInstanceId("testArchiveInstanceId2")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId3")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId4")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId5")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 15, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId6")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 16, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//
//            List<String> archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc =
//                    eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
//                            1L,
//                            "testSourceApplicationIntegrationId2",
//                            "testSourceApplicationInstanceId1",
//                            eventCategorizationService.getEventNamesPerInstanceStatus()
//                    );
//            assertThat(archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc).containsExactly(
//                    "testArchiveInstanceId5", "testArchiveInstanceId3", "testArchiveInstanceId4"
//            );
//        }
//
//        @Test
//        public void givenNoSourceApplicationIntegrationIdIdAndMatchingEventsShouldReturnArchiveInstanceIdsFromEvents() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .archiveInstanceId("testArchiveInstanceId2")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId3")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId4")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId5")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 15, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId6")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 16, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//
//            List<String> archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc =
//                    eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
//                            1L,
//                            null,
//                            "testSourceApplicationInstanceId1",
//                            eventCategorizationService.getEventNamesPerInstanceStatus()
//                    );
//            assertThat(archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc).containsExactly(
//                    "testArchiveInstanceId5", "testArchiveInstanceId3", "testArchiveInstanceId1", "testArchiveInstanceId4"
//            );
//        }
//
//        @Test
//        public void givenNoMatchingEventsShouldReturnEmptyList() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .archiveInstanceId("testArchiveInstanceId2")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId3")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId4")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId5")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 15, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId6")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 16, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//
//            List<String> archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc =
//                    eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
//                            3L,
//                            null,
//                            "testSourceApplicationInstanceId1",
//                            eventCategorizationService.getEventNamesPerInstanceStatus()
//                    );
//            assertThat(archiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc).isEmpty();
//        }
//
//    }
//
//
//    @Nested
//    class getTotalStatistics {
//
//        @Test
//        public void givenNullSourceApplicationIdListShouldReturnEmptyStatistics() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    null,
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//
//            assertThat(totalStatistics.getTotal()).isZero();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenEmptySourceApplicationIdListShouldReturnEmptyStatistics() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//
//            assertThat(totalStatistics.getTotal()).isZero();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenNoEventsWithMatchingSourceApplicationIdShouldReturnZeroes() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(2L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//
//            assertThat(totalStatistics.getTotal()).isZero();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenSingleInProgressInstanceWithMatchingSourceApplicationIdShouldReturnOneInProgress() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(1L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//            assertThat(totalStatistics.getTotal()).isOne();
//            assertThat(totalStatistics.getInProgress()).isOne();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenSingleTransferredInstanceWithMatchingSourceApplicationIdShouldReturnOneTransferred() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(1L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//            assertThat(totalStatistics.getTotal()).isOne();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isOne();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenSingleAbortedInstanceWithMatchingSourceApplicationIdShouldReturnOneAborted() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MANUALLY_REJECTED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(1L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//            assertThat(totalStatistics.getTotal()).isOne();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isOne();
//            assertThat(totalStatistics.getFailed()).isZero();
//        }
//
//        @Test
//        public void givenSingleFailedInstanceWithMatchingSourceApplicationIdShouldReturnOneFailed() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .archiveInstanceId("testArchiveInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MAPPING_ERROR.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(1L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//            assertThat(totalStatistics.getTotal()).isOne();
//            assertThat(totalStatistics.getInProgress()).isZero();
//            assertThat(totalStatistics.getTransferred()).isZero();
//            assertThat(totalStatistics.getAborted()).isZero();
//            assertThat(totalStatistics.getFailed()).isOne();
//        }
//
//        @Test
//        public void givenInstancesOfAllStatusesWithMatchingSourceApplicationIdShouldReturnStatisticsForAllStatuses() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId3")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId4")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId5")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MANUALLY_REJECTED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId6")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MANUALLY_REJECTED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId7")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MAPPING_ERROR.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.ERROR)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId8")
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MAPPING_ERROR.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.ERROR)
//                            .build()
//            ));
//            InstanceStatisticsProjection totalStatistics = eventRepository.getTotalStatistics(
//                    List.of(1L),
//                    eventCategorizationService.getEventNamesPerInstanceStatus()
//            );
//            assertThat(totalStatistics.getTotal()).isEqualTo(8);
//            assertThat(totalStatistics.getInProgress()).isEqualTo(2);
//            assertThat(totalStatistics.getTransferred()).isEqualTo(2);
//            assertThat(totalStatistics.getAborted()).isEqualTo(2);
//            assertThat(totalStatistics.getFailed()).isEqualTo(2);
//        }
//
//    }
//
//    @Nested
//    class getIntegrationStatistics {
//
//        @Test
//        public void givenNullFilterShouldReturnStatisticsForAll() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    null,
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(1);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithNullValuesShouldReturnStatisticsForAll() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .sourceApplicationIds(null)
//                            .sourceApplicationIntegrationIds(null)
//                            .integrationIds(null)
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(1);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithEmptyValuesShouldReturnStatisticsForAll() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .sourceApplicationIds(List.of())
//                            .sourceApplicationIntegrationIds(List.of())
//                            .integrationIds(List.of())
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(1);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithMatchingSourceApplicationIdShouldReturnStatisticsForSourceApplicationId() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .sourceApplicationIds(List.of(2L))
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(2);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithMatchingSourceApplicationIntegrationIdShouldReturnStatisticsForSourceApplicationId() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .sourceApplicationIntegrationIds(List.of("testSourceApplicationIntegrationId2"))
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(2);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithMatchingIntegrationIdShouldReturnStatisticsForIntegrationId() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .integrationIds(List.of(2L))
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(1);
//            IntegrationStatisticsProjection integrationStatistic = integrationStatistics.getContent().get(0);
//            assertThat(integrationStatistic.getIntegrationId()).isEqualTo(2);
//            assertThat(integrationStatistic.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistic.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistic.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistic.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistic.getFailed()).isEqualTo(0);
//        }
//
//        @Test
//        public void givenFilterWithNoMatchingEventsShouldReturnNoStatistics() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .integrationIds(List.of(3L))
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(0);
//        }
//
//        @Test
//        public void givenFilterWithMultipleMatchingEventsShouldReturnStatisticsForAllMatchingEvents() {
//            eventRepository.saveAllAndFlush(List.of(
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(1L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(1L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_RECEIVAL_ERROR.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId2")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                            .integrationId(2L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build(),
//                    EventEntity.builder()
//                            .instanceFlowHeaders(
//                                    InstanceFlowHeadersEmbeddable.builder()
//                                            .sourceApplicationId(2L)
//                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId3")
//                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                            .integrationId(3L)
//                                            .build()
//                            )
//                            .name(EventCategory.INSTANCE_MANUALLY_REJECTED.getEventName())
//                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
//                            .type(EventType.INFO)
//                            .build()
//            ));
//            Slice<IntegrationStatisticsProjection> integrationStatistics = eventRepository.getIntegrationStatistics(
//                    IntegrationStatisticsQueryFilter
//                            .builder()
//                            .sourceApplicationIds(List.of(1L, 2L, 3L))
//                            .sourceApplicationIntegrationIds(List.of(
//                                    "testSourceApplicationIntegrationId1",
//                                    "testSourceApplicationIntegrationId2",
//                                    "testSourceApplicationIntegrationId3"
//                            ))
//                            .integrationIds(List.of(1L, 2L, 3L))
//                            .build(),
//                    eventCategorizationService.getEventNamesPerInstanceStatus(),
//                    Pageable.unpaged()
//            );
//
//            assertThat(integrationStatistics).hasSize(3);
//            List<IntegrationStatisticsProjection> integrationStatisticsSorted = integrationStatistics.getContent()
//                    .stream()
//                    .sorted(Comparator.comparing(IntegrationStatisticsProjection::getIntegrationId))
//                    .toList();
//
//            IntegrationStatisticsProjection integrationStatistics0 = integrationStatisticsSorted.get(0);
//            assertThat(integrationStatistics0.getIntegrationId()).isEqualTo(1);
//            assertThat(integrationStatistics0.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistics0.getInProgress()).isEqualTo(1);
//            assertThat(integrationStatistics0.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistics0.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistics0.getFailed()).isEqualTo(0);
//
//            IntegrationStatisticsProjection integrationStatistics1 = integrationStatisticsSorted.get(1);
//            assertThat(integrationStatistics1.getIntegrationId()).isEqualTo(2);
//            assertThat(integrationStatistics1.getTotal()).isEqualTo(2);
//            assertThat(integrationStatistics1.getInProgress()).isEqualTo(0);
//            assertThat(integrationStatistics1.getTransferred()).isEqualTo(1);
//            assertThat(integrationStatistics1.getAborted()).isEqualTo(0);
//            assertThat(integrationStatistics1.getFailed()).isEqualTo(1);
//
//            IntegrationStatisticsProjection integrationStatistics2 = integrationStatisticsSorted.get(2);
//            assertThat(integrationStatistics2.getIntegrationId()).isEqualTo(3);
//            assertThat(integrationStatistics2.getTotal()).isEqualTo(1);
//            assertThat(integrationStatistics2.getInProgress()).isEqualTo(0);
//            assertThat(integrationStatistics2.getTransferred()).isEqualTo(0);
//            assertThat(integrationStatistics2.getAborted()).isEqualTo(1);
//            assertThat(integrationStatistics2.getFailed()).isEqualTo(0);
//        }
//    }
//
//}
