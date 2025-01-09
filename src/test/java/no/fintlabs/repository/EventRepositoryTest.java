package no.fintlabs.repository;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.context.testcontainers.ContainerCleanupType;
import no.fintlabs.context.testcontainers.RepositoryTestcontainersTest;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RepositoryTestcontainersTest(cleanupType = ContainerCleanupType.METHOD)
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    EventCategorizationService eventCategorizationService = new EventCategorizationService();

    @Getter
    @Builder
    private static class TestSourceApplicationAggregateInstanceId implements SourceApplicationAggregateInstanceId {
        private Long sourceApplicationId;
        private String sourceApplicationIntegrationId;
        private String sourceApplicationInstanceId;
    }

    @Test
    public void givenNoEventsWhenCalledShouldReturnEmpty() {
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
    public void givenNoEventsWithMatchingSourceApplicationAggregateIdWhenCalledShouldReturnEmpty() {
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
                        .name(EventCategory.INSTANCE_DISPATCHED.getName())
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
                        .name(EventCategory.INSTANCE_DISPATCHED.getName())
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
                        .name(EventCategory.INSTANCE_DISPATCHED.getName())
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
                        .name(EventCategory.INSTANCE_DISPATCHED.getName())
                        .timestamp(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
                        .type(EventType.INFO)
                        .build()
        ));

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
        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isPresent();
        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId.get()).isEqualTo(
                EventEntity.builder()
                        .instanceFlowHeaders(
                                InstanceFlowHeadersEmbeddable.builder()
                                        .sourceApplicationId(1L)
                                        .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                        .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                        .archiveInstanceId("testArchiveInstanceId1")
                                        .build()
                        )
                        .name(EventCategory.INSTANCE_DISPATCHED.getName())
                        .timestamp(OffsetDateTime.of(2024, 1, 1, 12, 1, 0, 0, ZoneOffset.UTC))
                        .type(EventType.INFO)
                        .build()
        );
    }


}
