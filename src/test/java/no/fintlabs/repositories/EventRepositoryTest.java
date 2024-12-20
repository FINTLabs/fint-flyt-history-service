package no.fintlabs.repositories;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.EventRepository;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.instance.InstanceInfo;
import no.fintlabs.model.instance.InstanceStatusFilter;
import no.fintlabs.model.statistics.InstanceStatistics;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repositories.utils.EventEntityGenerator;
import no.fintlabs.repositories.utils.EventSequence;
import no.fintlabs.repositories.utils.SequenceGenerationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@Slf4j
@ActiveProfiles("local-staging")
@DataJpaTest()
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    // TODO 20/12/2024 eivindmorch: Add isolated tests (populate db for each) with simple filter and sort asserts
    @Test
    public void generateEvents() {
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
        log.info("Generated entities");

        eventRepository.saveAllAndFlush(
                Stream.of(generatedEvents1, generatedEvents2)
                        .flatMap(Collection::stream)
                        .toList()
        );

        log.info("Persisted entities");
    }

    @Test
    public void instanceStatus() {
        long startTime = System.currentTimeMillis();
        Slice<InstanceInfo> instanceInfos =
                eventRepository.getInstanceInfo(
                        InstanceStatusFilter
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
                        PageRequest.of(
                                0,
                                10000,
                                Sort.by(Sort.Direction.DESC, "timestamp")
                        )
                );
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Result size=" + instanceInfos.getContent().size());
        log.info("Instances=" +
                 instanceInfos.getContent().stream()
                         .map(InstanceInfo::toString)
                         .collect(joining(",\r\n", "\r\n[", "\r\n]"))
        );
    }

    @Test
    public void statistics() {
        long startTime = System.currentTimeMillis();
        InstanceStatistics instanceStatistics =
                eventRepository.getTotalStatistics(
                        List.of(1L, 2L)
                );
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Page=" + instanceStatistics.toString());
    }

    @Test
    public void integrationStatistics() {
        long startTime = System.currentTimeMillis();
        Page<IntegrationStatistics> integrationStatistics =
                eventRepository.getIntegrationStatistics(
                        IntegrationStatisticsFilter
                                .builder()
                                .sourceApplicationIds(List.of(1L, 2L))
                                .build(),
                        PageRequest.of(
                                0,
                                1000
                        )
                );
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info("Elapsed time=" + elapsedTime + "ms");
        log.info("Page=" + integrationStatistics.toString());

    }

}
