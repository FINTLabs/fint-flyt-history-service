package no.fintlabs.repository;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.context.testcontainers.ContainerCleanupType;
import no.fintlabs.context.testcontainers.RepositoryTestcontainersTest;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.filters.InstanceInfoQueryFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import no.fintlabs.repository.projections.InstanceInfoProjection;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import no.fintlabs.repository.utils.EventEntityGenerator;
import no.fintlabs.repository.utils.EventSequence;
import no.fintlabs.repository.utils.SequenceGenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RepositoryTestcontainersTest(
        cleanupType = ContainerCleanupType.CLASS,
        cpuCount = 1,
        memorySize = 1024 * 1024 * 1024
)
public class EventRepositoryPerformanceTest {

    @Autowired
    EventRepository eventRepository;

    EventCategorizationService eventCategorizationService = new EventCategorizationService();

    private static boolean isInitialized = false;

    // TODO 08/01/2025 eivindmorch: Replace with proper initialisation of db data before first test
    @BeforeEach
    public void generateEvents() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        EventEntityGenerator eventEntityGenerator = new EventEntityGenerator(42L);
        List<EventEntity> generatedEvents1 = eventEntityGenerator.generateEvents(
                1L,
                "testIntegrationId1",
                10L,
                OffsetDateTime.of(2024, 1, 6, 18, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        SequenceGenerationConfig
                                .builder()
                                .sourceApplicationInstanceIdOverride("testSourceApplicationInstanceId1")
                                .eventSequence(EventSequence.HAPPY_CASE)
                                .numberOfSequences(200)
                                .build(),
                        SequenceGenerationConfig
                                .builder()
                                .eventSequence(EventSequence.MAPPING_ERROR)
                                .numberOfSequences(20000)
                                .build(),
                        SequenceGenerationConfig
                                .builder()
                                .eventSequence(EventSequence.RECEIVAL_ERROR)
                                .numberOfSequences(3)
                                .build()
                )
        );

        List<EventEntity> generatedEvents2 = eventEntityGenerator.generateEvents(
                2L,
                "testIntegrationId2",
                22L,
                OffsetDateTime.of(2024, 1, 6, 19, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        SequenceGenerationConfig
                                .builder()
                                .eventSequence(EventSequence.DISPATCH_ERROR_RETRY_SUCCESS)
                                .numberOfSequences(200)
                                .build()
                )
        );
        eventRepository.saveAll(generatedEvents1);
        eventRepository.saveAll(generatedEvents2);
        eventRepository.flush();
    }

    @Test
    public void instanceStatus() {
        long startTime = System.currentTimeMillis();
        Slice<InstanceInfoProjection> instanceInfos =
                eventRepository.getInstanceInfo(
                        InstanceInfoQueryFilter
                                .builder()
                                .sourceApplicationIds(List.of(1L, 2L))
                                //.statuses(List.of(InstanceStatus.FAILED))
                                //.storageStatuses(List.of(InstanceStorageStatus.NEVER_STORED))
                                //.associatedEventNames(List.of(InstanceStatusEvent.INSTANCE_REQUESTED_FOR_RETRY.getName()))
                                //.sourceApplicationIntegrationIds(List.of("testIntegrationId1"))
                                //.integrationIds(List.of(10L))
                                //.sourceApplicationInstanceIds(List.of("testSourceApplicationInstanceId1"))
                                //.latestStatusTimestampMin(OffsetDateTime.of(2024, 11, 6, 18, 0, 0, 0, ZoneOffset.UTC))
                                //.latestStatusTimestampMax(OffsetDateTime.of(2024, 12, 6, 19, 10, 0, 0, ZoneOffset.UTC))
                                //.destinationIds(List.of("RBRP1Ykvga"))
                                .build(),
                        eventCategorizationService.getAllInstanceStatusEventNames(),
                        eventCategorizationService.getAllInstanceStorageStatusEventNames(),
                        PageRequest.of(
                                0,
                                20000,
                                Sort.by(Sort.Direction.DESC, "timestamp")
                        )
                );
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Result size=" + instanceInfos.getContent().size());
//        log.info("Instances=" +
//                 instanceInfos.getContent().stream()
//                         .map(InstanceInfoProjection::toString)
//                         .collect(joining(",\r\n", "\r\n[", "\r\n]"))
//        );
    }

    @Test
    public void findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc() {
        List<String> archiveInstanceIdsOrderedByTimestamp =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        1L,
                        "testIntegrationId1",
                        "testSourceApplicationInstanceId1",
                        eventCategorizationService.getEventNamesPerInstanceStatus()
                );

        assertThat(archiveInstanceIdsOrderedByTimestamp).hasSize(200);
        assertThat(archiveInstanceIdsOrderedByTimestamp.get(0)).isEqualTo("RBRP1Ykvga");
        assertThat(archiveInstanceIdsOrderedByTimestamp.get(199)).isEqualTo("vMRdgF3tqQ");
    }

    @Test
    public void statistics() {
        long startTime = System.currentTimeMillis();
        InstanceStatisticsProjection instanceStatisticsProjection =
                eventRepository.getTotalStatistics(
                        List.of(1L, 2L),
                        eventCategorizationService.getEventNamesPerInstanceStatus()
                );
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Page=" + instanceStatisticsProjection.toString());

        assertThat(instanceStatisticsProjection.getTotal()).isEqualTo(20204L);
        assertThat(instanceStatisticsProjection.getInProgress()).isEqualTo(0L);
        assertThat(instanceStatisticsProjection.getTransferred()).isEqualTo(201L);
        assertThat(instanceStatisticsProjection.getAborted()).isEqualTo(0L);
        assertThat(instanceStatisticsProjection.getFailed()).isEqualTo(20003L);
    }

    @Test
    public void integrationStatistics() {
        long startTime = System.currentTimeMillis();
        Slice<IntegrationStatisticsProjection> integrationStatistics =
                eventRepository.getIntegrationStatistics(
                        IntegrationStatisticsQueryFilter
                                .builder()
                                .sourceApplicationIds(List.of(1L, 2L))
                                .build(),
                        eventCategorizationService.getEventNamesPerInstanceStatus(),
                        PageRequest.of(
                                0,
                                1000
                        )
                );
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Page=" + integrationStatistics.toString());

        assertThat(integrationStatistics.getContent().size()).isEqualTo(2);

        IntegrationStatisticsProjection integrationStatistics1 = integrationStatistics.getContent().get(0);
        assertThat(integrationStatistics1.getTotal()).isEqualTo(20004L);
        assertThat(integrationStatistics1.getInProgress()).isEqualTo(0L);
        assertThat(integrationStatistics1.getTransferred()).isEqualTo(1L);
        assertThat(integrationStatistics1.getAborted()).isEqualTo(0L);
        assertThat(integrationStatistics1.getFailed()).isEqualTo(20003L);

        IntegrationStatisticsProjection integrationStatistics2 = integrationStatistics.getContent().get(1);
        assertThat(integrationStatistics2.getTotal()).isEqualTo(200L);
        assertThat(integrationStatistics2.getInProgress()).isEqualTo(0L);
        assertThat(integrationStatistics2.getTransferred()).isEqualTo(200L);
        assertThat(integrationStatistics2.getAborted()).isEqualTo(0L);
        assertThat(integrationStatistics2.getFailed()).isEqualTo(0L);
    }

}
