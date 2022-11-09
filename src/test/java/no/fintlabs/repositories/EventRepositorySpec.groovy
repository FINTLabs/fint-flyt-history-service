package no.fintlabs.repositories

import no.fintlabs.model.Event
import no.fintlabs.model.EventType
import no.fintlabs.model.InstanceFlowHeadersEmbeddable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

import static no.fintlabs.EventTopicNames.*

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@DirtiesContext
class EventRepositorySpec extends Specification {

    @Autowired
    EventRepository eventRepository

    Event eventApplicableForGettingArchiveInstanceId

    def setup() {
        eventApplicableForGettingArchiveInstanceId = Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationId(1)
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
                                .instanceId(2)
                                .configurationId(3)
                                .archiveInstanceId("testArchiveInstanceId1")
                                .build()
                )
                .name(INSTANCE_DISPATCHED)
                .type(EventType.INFO)
                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 12, 30), ZoneOffset.UTC))
                .build()
    }

    def "should return only latest event for each source application instance id"() {
        given:
        Event event1 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30))
        Event event2 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 32))
        Event event3 = createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31))
        Event event4 = createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 29))
        eventRepository.saveAll(List.of(event1, event2, event3, event4))

        when:
        Page<Event> events = eventRepository.findLatestEventPerSourceApplicationInstanceId(Pageable.unpaged())

        then:
        events.size() == 2
        events.containsAll(event2, event4)
    }

    def "should return archive instance id from event that is of type INFO, is a instance dispatched event, and has matching source application and source application instance ids"() {
        given:
        eventRepository.save(eventApplicableForGettingArchiveInstanceId)

        when:
        Optional<String> archiveInstanceId = eventRepository.findArchiveInstanceId(
                1,
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveInstanceId.isPresent()
        archiveInstanceId.get() == "testArchiveInstanceId1"
    }

    def "should not return archive instance id of event with type ERROR"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .type(EventType.ERROR)
                        .build()
        )

        when:
        Optional<String> result = eventRepository.findArchiveInstanceId(
                1,
                "testSourceApplicationInstanceId1"
        )

        then:
        result.isEmpty()
    }

    def "should not return archive instance id if event is not a instance dispatched event"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .name(INSTANCE_MAPPED)
                        .build()
        )

        when:
        Optional<String> result = eventRepository.findArchiveInstanceId(
                1,
                "testSourceApplicationInstanceId1"
        )

        then:
        result.isEmpty()
    }

    def "should not return archive instance id of event does not have a matching source application"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveInstanceId.instanceFlowHeaders
                                        .toBuilder()
                                        .sourceApplicationId(2)
                                        .build()
                        )
                        .build()
        )

        when:
        Optional<String> result = eventRepository.findArchiveInstanceId(
                1,
                "testSourceApplicationInstanceId1"
        )

        then:
        result.isEmpty()
    }

    def "should not return archive instance id of event does not have a matching source application instance id"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveInstanceId.instanceFlowHeaders
                                        .toBuilder()
                                        .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
                                        .build()
                        )
                        .build()
        )

        when:
        Optional<String> result = eventRepository.findArchiveInstanceId(
                1,
                "testSourceApplicationInstanceId1"
        )

        then:
        result.isEmpty()
    }

    def 'should return number of dispatched instances'() {
        given:
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),

                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED),
        ))

        when:
        long numberOfDispatchedInstances = eventRepository.countDispatchedInstances()

        then:
        numberOfDispatchedInstances == 4
    }

    def 'should return number of dispatched instances per integration id'() {
        given:
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),

                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED),
        ))

        when:
        Collection<EventRepository.IntegrationIdAndCount> numberOfDispatchedInstancesPerIntegrationId = eventRepository.countDispatchedInstancesPerIntegrationId()

        then:
        numberOfDispatchedInstancesPerIntegrationId.size() == 2

        numberOfDispatchedInstancesPerIntegrationId[0].getIntegrationId() == "1"
        numberOfDispatchedInstancesPerIntegrationId[0].getCount() == 1

        numberOfDispatchedInstancesPerIntegrationId[1].getIntegrationId() == "2"
        numberOfDispatchedInstancesPerIntegrationId[1].getCount() == 3
    }

    private Event createNamedEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, String name) {
        return Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                                .build()
                )
                .type(eventType)
                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 13, 30), ZoneOffset.UTC))
                .name(name)
                .build()
    }

    def 'should return number of current instance errors'() {
        given:
        eventRepository.saveAll(List.of(
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),

                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),

                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),

                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),

                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28)),
        ))

        when:
        long numberOfCurrentInstanceErrors = eventRepository.countCurrentInstanceErrors()

        then:
        numberOfCurrentInstanceErrors == 3
    }

    def 'should return number of current instance errors per integration id'() {
        given:
        eventRepository.saveAll(List.of(
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),

                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),

                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),

                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),

                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28)),
        ))

        when:
        Collection<EventRepository.IntegrationIdAndCount> numberOfErrorsPerIntegrationId = eventRepository.countCurrentInstanceErrorsPerIntegrationId()


        then:
        numberOfErrorsPerIntegrationId[0].getIntegrationId() == "1"
        numberOfErrorsPerIntegrationId[0].getCount() == 2

        numberOfErrorsPerIntegrationId[1].getIntegrationId() == "4"
        numberOfErrorsPerIntegrationId[1].getCount() == 1
    }

    private Event createUnnamedTimestampEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, LocalDateTime timestamp) {
        return Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                                .build()
                )
                .type(eventType)
                .timestamp(OffsetDateTime.of(timestamp, ZoneOffset.UTC))
                .build()
    }

}
