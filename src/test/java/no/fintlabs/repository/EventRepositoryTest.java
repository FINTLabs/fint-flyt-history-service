package no.fintlabs.repository;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    EventCategorizationService eventCategorizationService = new EventCategorizationService();

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

    @Getter
    @Builder
    private static class TestSourceApplicationAggregateInstanceId implements SourceApplicationAggregateInstanceId {
        private Long sourceApplicationId;
        private String sourceApplicationIntegrationId;
        private String sourceApplicationInstanceId;
    }

    @Nested
    class findLatestStatusEventBySourceApplicationAggregateInstanceId {

        @Test
        public void givenNoEventsWhenFindLatestStatusEventBySourceApplicationAggregateInstanceIdShouldReturnEmpty() {
            Optional<EventEntity> latestStatusEventBySourceApplicationAggregateInstanceId =
                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                            TestSourceApplicationAggregateInstanceId
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                    .build(),
                            eventCategorizationService.getAllInstanceStatusEventNames()
                    );

            assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEmpty();
        }

        @Test
        public void givenNoEventsWithMatchingSourceApplicationAggregateIdWhenFindLatestStatusEventBySourceApplicationAggregateInstanceIdShouldReturnEmpty() {
            eventRepository.save(
                    EventEntity.builder()
                            .instanceFlowHeaders(
                                    InstanceFlowHeadersEmbeddable.builder()
                                            .sourceApplicationId(1L)
                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
                                            .archiveInstanceId("testArchiveInstanceId1")
                                            .build()
                            )
                            .name("testName1")
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            );

            Optional<EventEntity> latestStatusEventBySourceApplicationAggregateInstanceId =
                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                            TestSourceApplicationAggregateInstanceId
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                    .build(),
                            eventCategorizationService.getAllInstanceStatusEventNames()
                    );

            assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEmpty();
        }

        @Test
        public void givenEventsWithMatchingSourceApplicationAggregateInstanceIdWhenCalledShouldReturnLatestStatusEvent() {
            eventRepository.saveAll(List.of(
                    EventEntity.builder()
                            .instanceFlowHeaders(
                                    InstanceFlowHeadersEmbeddable.builder()
                                            .sourceApplicationId(1L)
                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId0")
                                            .archiveInstanceId("testArchiveInstanceId1")
                                            .build()
                            )
                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity.builder()
                            .instanceFlowHeaders(
                                    InstanceFlowHeadersEmbeddable.builder()
                                            .sourceApplicationId(1L)
                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                            .archiveInstanceId("testArchiveInstanceId1")
                                            .build()
                            )
                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity.builder()
                            .instanceFlowHeaders(
                                    InstanceFlowHeadersEmbeddable.builder()
                                            .sourceApplicationId(1L)
                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                            .archiveInstanceId("testArchiveInstanceId1")
                                            .build()
                            )
                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 1, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build(),
                    EventEntity.builder()
                            .instanceFlowHeaders(
                                    InstanceFlowHeadersEmbeddable.builder()
                                            .sourceApplicationId(1L)
                                            .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                            .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
                                            .archiveInstanceId("testArchiveInstanceId1")
                                            .build()
                            )
                            .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
                            .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
                            .type(EventType.INFO)
                            .build()
            ));

            Optional<EventEntity> returnedOptionalEvent =
                    eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                            TestSourceApplicationAggregateInstanceId
                                    .builder()
                                    .sourceApplicationId(1L)
                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                    .build(),
                            eventCategorizationService.getAllInstanceStatusEventNames()
                    );

            assertThat(returnedOptionalEvent).isPresent();
            EventEntity returnedEvent = returnedOptionalEvent.get();
            assertThat(returnedEvent)
                    .usingRecursiveComparison()
                    .ignoringFields("id")
                    .withEqualsForType(
                            OffsetDateTime::isEqual,
                            OffsetDateTime.class
                    )
                    .isEqualTo(
                            EventEntity.builder()
                                    .instanceFlowHeaders(
                                            InstanceFlowHeadersEmbeddable.builder()
                                                    .sourceApplicationId(1L)
                                                    .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                                    .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                                    .archiveInstanceId("testArchiveInstanceId1")
                                                    .build()
                                    )
                                    .name(EventCategory.INSTANCE_DISPATCHED.getEventName())
                                    .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 1, 0, 0, ZoneOffset.UTC))
                                    .type(EventType.INFO)
                                    .build()
                    );
        }
    }


    // TODO 02/04/2025 eivindmorch: Add
    @Nested
    class findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc {

        @Test
        public void givenMatchingEventsShouldReturnArchiveInstanceIdsFromEvents() {

        }

        @Test
        public void givenNoMatchingEventsShouldReturnEmptyList() {

        }

    }


    // TODO 02/04/2025 eivindmorch: Add
    @Nested
    class getTotalStatistics {

        @Test
        public void givenSingleInProgressInstanceWithMatchingSourceApplicationIdShouldReturnOneInProgress() {

        }

        @Test
        public void givenSingleTransferredInstanceWithMatchingSourceApplicationIdShouldReturnOneTransferred() {

        }

        @Test
        public void givenSingleAbortedInstanceWithMatchingSourceApplicationIdShouldReturnOneAborted() {

        }

        @Test
        public void givenSingleFailedInstanceWithMatchingSourceApplicationIdShouldReturnOneFailed() {

        }

        @Test
        public void givenInstancesOfAllStatusesWithMatchingSourceApplicationIdShouldReturnStatisticsForAllStatuses() {

        }

        @Test
        public void givenNoEventsWithMatchingSourceApplicationIdShouldReturnZeroes() {

        }

    }

    @Nested
    class getIntegrationStatistics {
        // TODO 06/03/2025 eivindmorch: With matching events
        // TODO 06/03/2025 eivindmorch: With no matching events
    }

}
