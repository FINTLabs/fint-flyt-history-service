package no.fintlabs.repositories;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.InstanceStatus;
import no.fintlabs.model.InstanceStatusFilter;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.repositories.utils.EventEntityGenerator;
import no.fintlabs.repositories.utils.EventSequence;
import no.fintlabs.repositories.utils.SequenceGenerationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ActiveProfiles("local-staging")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    @Test
    public void a() {
        EventEntityGenerator eventEntityGenerator = new EventEntityGenerator(42L);
        List<EventEntity> generatedEvents1 = eventEntityGenerator.generateEvents(
                1L,
                "testIntegrationId1",
                10L,
                OffsetDateTime.of(2024, 12, 6, 18, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        SequenceGenerationConfig
                                .builder()
                                .sourceApplicationInstanceIdOverride("testSourceApplicationInstanceId1")
                                .eventSequence(EventSequence.HAPPY_CASE)
                                .numberOfSequences(2)
                                .build(),
                        SequenceGenerationConfig
                                .builder()
                                .eventSequence(EventSequence.MAPPING_ERROR)
                                .numberOfSequences(200)
                                .build()
                )
        );

        List<EventEntity> generatedEvents2 = eventEntityGenerator.generateEvents(
                2L,
                "testIntegrationId2",
                22L,
                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 12, 6, 20, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        SequenceGenerationConfig
                                .builder()
                                .eventSequence(EventSequence.DISPATCH_ERROR)
                                .numberOfSequences(200)
                                .build()
                )
        );
        log.info("Generated entities");

        eventRepository.saveAll(generatedEvents1);
        eventRepository.saveAll(generatedEvents2);

        long startTime = System.currentTimeMillis();
        Page<InstanceStatus> instanceStatuses =
                eventRepository.getInstanceStatuses(
                        InstanceStatusFilter
                                .builder()
                                .sourceApplicationIds(List.of(1L, 2L))
                                //.statusEventNames(List.of(EventInfo.INSTANCE_MAPPED.getName()))
                                //.storageEventNames(List.of(EventInfo.INSTANCE_DELETED.getName()))
                                //.sourceApplicationIntegrationIds(List.of("testIntegrationId1"))
                                //.sourceApplicationInstanceIds(List.of("testSourceApplicationInstanceId1", "testSourceApplicationInstanceId2"))
                                //.latestStatusTimestampMin(OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC))
                                //.latestStatusTimestampMax(OffsetDateTime.of(2024, 12, 6, 20, 0, 0, 0, ZoneOffset.UTC))
                                .build(),
                        PageRequest.of(
                                0,
                                1000,
                                Sort.by(List.of(
                                        Sort.Order.desc("instanceFlowHeaders.sourceApplicationId"),
                                        Sort.Order.asc("timestamp")
                                ))
                        )
                );
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.error("Elapsed time=" + elapsedTime + "ms");

        assertThat(instanceStatuses).isEqualTo(List.of());
    }

//    @Test
//    public void nat() {
//        EventEntityGenerator eventEntityGenerator = new EventEntityGenerator(42L);
//        List<EventEntity> generatedEvents1 = eventEntityGenerator.generateEvents(
//                1L,
//                "testIntegrationId1",
//                10L,
//                OffsetDateTime.of(2024, 12, 6, 18, 0, 0, 0, ZoneOffset.UTC),
//                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
//                List.of(
//                        SequenceGenerationConfig
//                                .builder()
//                                .sourceApplicationInstanceIdOverride("testSourceApplicationInstanceId1")
//                                .eventSequence(EventSequence.HAPPY_CASE)
//                                .numberOfSequences(200)
//                                .build(),
//                        SequenceGenerationConfig
//                                .builder()
//                                .eventSequence(EventSequence.DISPATCH_ERROR)
//                                .numberOfSequences(20000)
//                                .build()
//                )
//        );
//
//        List<EventEntity> generatedEvents2 = eventEntityGenerator.generateEvents(
//                2L,
//                "testIntegrationId2",
//                22L,
//                OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC),
//                OffsetDateTime.of(2024, 12, 6, 20, 0, 0, 0, ZoneOffset.UTC),
//                List.of(
//                        SequenceGenerationConfig
//                                .builder()
//                                .eventSequence(EventSequence.DISPATCH_ERROR)
//                                .numberOfSequences(200)
//                                .build()
//                )
//        );
//        log.info("Generated entities");
//
//        eventRepository.saveAll(generatedEvents1);
//        eventRepository.saveAll(generatedEvents2);
//
//        long startTime = System.currentTimeMillis();
//        Page<InstanceStatus> instanceStatuses =
//                eventRepository.getInstanceStatuses(
//                        InstanceFilter
//                                .builder()
//                                //.statusEventNames(List.of(EventInfo.INSTANCE_DISPATCHED.getName()))
//                                //.storageEventNames(List.of(EventInfo.INSTANCE_DELETED.getName()))
//                                //.sourceApplicationIntegrationIds(List.of("testIntegrationId1"))
//                                //.sourceApplicationInstanceIds(List.of("testSourceApplicationInstanceId1", "testSourceApplicationInstanceId2"))
//                                //.latestStatusTimestampMin(OffsetDateTime.of(2024, 12, 6, 18, 0, 0, 0, ZoneOffset.UTC))
//                                //.latestStatusTimestampMax(OffsetDateTime.of(2024, 12, 6, 19, 0, 0, 0, ZoneOffset.UTC))
//                                .build(),
//                        PageRequest.of(0, 10)
//                        //   EventInfo.getAllStatusEventNames(),
//                        //  EventInfo.getAllStorageEventNames()
//                );
//        long elapsedTime = System.currentTimeMillis() - startTime;
//
//        log.error("Elapsed time=" + elapsedTime + "ms");
//
//        assertThat(instanceStatuses).isEqualTo(List.of());
//    }

//    @BeforeEach
//    public void setup() {
//        eventEntityApplicableForGettingArchiveInstanceId = EventEntity
//                .builder()
//                .instanceFlowHeaders(
//                        InstanceFlowHeadersEmbeddable
//                                .builder()
//                                .sourceApplicationId(1L)
//                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
//                                .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
//                                .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
//                                .instanceId(2L)
//                                .configurationId(3L)
//                                .archiveInstanceId("testArchiveInstanceId1")
//                                .build()
//                )
//                .name(INSTANCE_DISPATCHED)
//                .type(EventType.INFO)
//                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 12, 30), ZoneOffset.UTC))
//                .build();
//    }
//
//    @Test
//    public void shouldReturnOnlyLatestEventForEachSourceApplicationInstanceId() {
//        EventEntity eventEntity1 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30));
//        eventEntity1.setName(INSTANCE_RECEIVED);
//
//        EventEntity eventEntity2 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 32));
//        eventEntity2.setName(INSTANCE_RECEIVED);
//
//        EventEntity eventEntity3 = createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31));
//        eventEntity3.setName(INSTANCE_DELETED);
//
//        EventEntity eventEntity4 = createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 29));
//        eventEntity4.setName(INSTANCE_RECEIVED);
//
//        eventRepository.saveAll(List.of(eventEntity1, eventEntity2, eventEntity3, eventEntity4));
//
//        Page<EventEntity> events = eventRepository.findLatestEventNotDeletedPerSourceApplicationInstanceId(Pageable.unpaged());
//
//        assertEquals(2, events.getTotalElements());
//        assertTrue(events.getContent().containsAll(Arrays.asList(eventEntity2, eventEntity4)));
//    }
//
//    @Test
//    public void shouldReturnArchiveInstanceIdFromEventThatIsOfTypeInfoIsADispatchedEventAndHasMatchingSourceApplicationAndSourceApplicationInstanceIds() {
//        eventRepository.save(eventEntityApplicableForGettingArchiveInstanceId);
//
//        Optional<String> archiveInstanceId = eventRepository.findLatestArchiveInstanceId(
//                1L,
//                "testSourceApplicationInstanceId1"
//        );
//
//        assertTrue(archiveInstanceId.isPresent());
//        assertEquals("testArchiveInstanceId1", archiveInstanceId.get());
//    }
//
//    @Test
//    public void shouldNotReturnArchiveInstanceIdOfEventWithTypeERROR() {
//        eventRepository.save(
//                eventEntityApplicableForGettingArchiveInstanceId
//                        .toBuilder()
//                        .type(EventType.ERROR)
//                        .build()
//        );
//
//        Optional<String> result = eventRepository.findLatestArchiveInstanceId(
//                1L,
//                "testSourceApplicationInstanceId1"
//        );
//
//        assertTrue(result.isEmpty());
//    }
//
//    @Test
//    public void shouldNotReturnArchiveInstanceIdIfEventIsNotAnInstanceDispatchedEvent() {
//        eventRepository.save(
//                eventEntityApplicableForGettingArchiveInstanceId
//                        .toBuilder()
//                        .name(INSTANCE_MAPPED)
//                        .build()
//        );
//
//        Optional<String> result = eventRepository.findLatestArchiveInstanceId(
//                1L,
//                "testSourceApplicationInstanceId1"
//        );
//
//        assertTrue(result.isEmpty());
//    }
//
//    @Test
//    public void shouldNotReturnArchiveInstanceIdIfEventDoesNotHaveAMatchingSourceApplication() {
//        eventRepository.save(
//                eventEntityApplicableForGettingArchiveInstanceId
//                        .toBuilder()
//                        .instanceFlowHeaders(
//                                eventEntityApplicableForGettingArchiveInstanceId.getInstanceFlowHeaders()
//                                        .toBuilder()
//                                        .sourceApplicationId(2L)
//                                        .build()
//                        )
//                        .build()
//        );
//
//        Optional<String> result = eventRepository.findLatestArchiveInstanceId(
//                1L,
//                "testSourceApplicationInstanceId1"
//        );
//
//        assertTrue(result.isEmpty());
//    }
//
//    @Test
//    public void shouldNotReturnArchiveInstanceIdIfEventDoesNotHaveAMatchingSourceApplicationInstanceId() {
//        eventRepository.save(
//                eventEntityApplicableForGettingArchiveInstanceId
//                        .toBuilder()
//                        .instanceFlowHeaders(
//                                eventEntityApplicableForGettingArchiveInstanceId.getInstanceFlowHeaders()
//                                        .toBuilder()
//                                        .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
//                                        .build()
//                        )
//                        .build()
//        );
//
//        Optional<String> result = eventRepository.findLatestArchiveInstanceId(
//                1L,
//                "testSourceApplicationInstanceId1"
//        );
//
//        assertTrue(result.isEmpty());
//    }
//
//    @Test
//    public void shouldReturnNumberOfDispatchedInstances() {
//        eventRepository.saveAll(List.of(
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),
//
//                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),
//
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
//
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED)
//        ));
//
//        long numberOfDispatchedInstances = eventRepository.countDispatchedInstances();
//
//        assertEquals(4, numberOfDispatchedInstances);
//    }
//
//    @Test
//    public void shouldReturnNumberOfDispatchedInstancesPerIntegrationId() {
//        eventRepository.saveAll(List.of(
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),
//
//                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),
//
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
//                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
//
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
//                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED)
//        ));
//
//        Collection<EventRepository.IntegrationIdAndCount> numberOfDispatchedInstancesPerIntegrationId = eventRepository.countDispatchedInstancesPerIntegrationId();
//
//        List<EventRepository.IntegrationIdAndCount> list = numberOfDispatchedInstancesPerIntegrationId.stream().toList();
//
//        assertEquals(2, list.size());
//
//        assertEquals("1", list.get(0).getIntegrationId());
//        assertEquals(1, list.get(0).getCount());
//
//        assertEquals("2", list.get(1).getIntegrationId());
//        assertEquals(3, list.get(1).getCount());
//    }
//
//    @Test
//    public void shouldReturnNumberOfCurrentInstanceErrors() {
//        eventRepository.saveAll(List.of(
//                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
//                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
//                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
//
//                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),
//
//                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
//                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),
//
//                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),
//
//                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28))
//        ));
//
//        long numberOfCurrentInstanceErrors = eventRepository.countCurrentInstanceErrors();
//
//        assertEquals(3, numberOfCurrentInstanceErrors);
//    }
//
//    @Test
//    public void shouldReturnNumberOfCurrentInstanceErrorsPerIntegrationId() {
//        eventRepository.saveAll(List.of(
//                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
//                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
//                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
//
//                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),
//
//                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
//                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),
//
//                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),
//
//                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28))
//        ));
//
//        Collection<EventRepository.IntegrationIdAndCount> numberOfErrorsPerIntegrationId = eventRepository.countCurrentInstanceErrorsPerIntegrationId();
//
//        List<EventRepository.IntegrationIdAndCount> list = numberOfErrorsPerIntegrationId.stream().toList();
//
//        assertEquals("1", list.get(0).getIntegrationId());
//        assertEquals(2, list.get(0).getCount());
//
//        assertEquals("4", list.get(1).getIntegrationId());
//        assertEquals(1, list.get(1).getCount());
//    }
//
//    private EventEntity createUnnamedTimestampEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, LocalDateTime timestamp) {
//        return EventEntity
//                .builder()
//                .instanceFlowHeaders(
//                        InstanceFlowHeadersEmbeddable
//                                .builder()
//                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
//                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
//                                .build()
//                )
//                .type(eventType)
//                .timestamp(OffsetDateTime.of(timestamp, ZoneOffset.UTC))
//                .build();
//    }
//
//    private EventEntity createNamedEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, String name) {
//        return EventEntity
//                .builder()
//                .instanceFlowHeaders(
//                        InstanceFlowHeadersEmbeddable
//                                .builder()
//                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
//                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
//                                .build()
//                )
//                .type(eventType)
//                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 13, 30), ZoneOffset.UTC))
//                .name(name)
//                .build();
//    }

}
