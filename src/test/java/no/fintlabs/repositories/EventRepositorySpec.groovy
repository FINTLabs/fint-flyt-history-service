package no.fintlabs.repositories

import no.fintlabs.model.Event
import no.fintlabs.model.EventType
import no.fintlabs.model.InstanceFlowHeadersEmbeddable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@DirtiesContext
class EventRepositorySpec extends Specification {

    @Autowired
    EventRepository eventRepository

    Event eventApplicableForGettingArchiveCaseId

    def setup() {
        eventApplicableForGettingArchiveCaseId = Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .orgId("orgId")
                                .sourceApplicationId("testSourceApplicationId1")
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                .correlationId("testCorrelationId1")
                                .instanceId("testInstanceId1")
                                .configurationId("testConfigurationId1")
                                .archiveCaseId("testArchiveCaseId1")
                                .build()
                )
                .name("case-dispatched")
                .type(EventType.INFO)
                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 12, 30), ZoneOffset.UTC))
                .build()
    }

    def "should return archiveCaseId from event that is of type INFO, is a case dispatched event, and has matching source application and source application instance ids"() {
        given:
        eventRepository.save(eventApplicableForGettingArchiveCaseId)

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseId(
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isPresent()
        archiveCaseId.get() == "testArchiveCaseId1"
    }

    def "should not return case id of event with type ERROR"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveCaseId
                        .toBuilder()
                        .type(EventType.ERROR)
                        .build()
        )

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseId(
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isEmpty()
    }

    def "should not return case id of event is not a case dispatched event"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveCaseId
                        .toBuilder()
                        .name('new-case')
                        .build()
        )

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseId(
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isEmpty()
    }

    def "should not return case id of event does not have a matching source application"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveCaseId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveCaseId.instanceFlowHeaders
                                        .toBuilder()
                                        .sourceApplicationId("testSourceApplicationId2")
                                        .build()
                        )
                        .build()
        )

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseId(
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isEmpty()
    }

    def "should not return case id of event does not have a matching source application instance id"() {
        given:
        eventRepository.save(
                eventApplicableForGettingArchiveCaseId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveCaseId.instanceFlowHeaders
                                        .toBuilder()
                                        .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
                                        .build()
                        )
                        .build()
        )

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseId(
                "testSourceApplicationId1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isEmpty()
    }

    def 'should return number of dispatched instances'() {
        given:
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, "incoming-instance"),
                createNamedEvent("1", "1", EventType.INFO, "new-instance"),
                createNamedEvent("1", "1", EventType.INFO, "new-case"),
                createNamedEvent("1", "1", EventType.INFO, "case-dispatched"),

                createNamedEvent("1", "2", EventType.INFO, "incoming-instance"),
                createNamedEvent("1", "2", EventType.INFO, "new-instance"),
                createNamedEvent("1", "2", EventType.ERROR, "instance-to-case-mapping"),

                createNamedEvent("2", "3", EventType.INFO, "incoming-instance"),
                createNamedEvent("2", "3", EventType.INFO, "new-instance"),
                createNamedEvent("2", "3", EventType.INFO, "new-case"),
                createNamedEvent("2", "3", EventType.INFO, "case-dispatched"),
                createNamedEvent("2", "3", EventType.INFO, "case-dispatched"),

                createNamedEvent("2", "4", EventType.INFO, "incoming-instance"),
                createNamedEvent("2", "4", EventType.INFO, "new-instance"),
                createNamedEvent("2", "4", EventType.INFO, "new-case"),
                createNamedEvent("2", "4", EventType.INFO, "case-dispatched"),
        ))

        when:
        long numberOfDispatchedInstances = eventRepository.countDispatchedInstances()

        then:
        numberOfDispatchedInstances == 4
    }

    def 'should return number of dispatched instances per integration id'() {
        given:
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, "incoming-instance"),
                createNamedEvent("1", "1", EventType.INFO, "new-instance"),
                createNamedEvent("1", "1", EventType.INFO, "new-case"),
                createNamedEvent("1", "1", EventType.INFO, "case-dispatched"),

                createNamedEvent("1", "2", EventType.INFO, "incoming-instance"),
                createNamedEvent("1", "2", EventType.INFO, "new-instance"),
                createNamedEvent("1", "2", EventType.ERROR, "instance-to-case-mapping"),

                createNamedEvent("2", "3", EventType.INFO, "incoming-instance"),
                createNamedEvent("2", "3", EventType.INFO, "new-instance"),
                createNamedEvent("2", "3", EventType.INFO, "new-case"),
                createNamedEvent("2", "3", EventType.INFO, "case-dispatched"),
                createNamedEvent("2", "3", EventType.INFO, "case-dispatched"),

                createNamedEvent("2", "4", EventType.INFO, "incoming-instance"),
                createNamedEvent("2", "4", EventType.INFO, "new-instance"),
                createNamedEvent("2", "4", EventType.INFO, "new-case"),
                createNamedEvent("2", "4", EventType.INFO, "case-dispatched"),
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
