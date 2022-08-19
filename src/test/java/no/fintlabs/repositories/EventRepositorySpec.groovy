package no.fintlabs.repositories


import no.fintlabs.model.Event
import no.fintlabs.model.EventType
import no.fintlabs.model.InstanceFlowHeadersEmbeddable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification

import java.time.LocalDateTime

@DataJpaTest
@DirtiesContext
class EventRepositorySpec extends Specification {

    @Autowired
    EventRepository eventRepository

    Event eventApplicableForGettingArchiveCaseId;

    def setup() {
        eventApplicableForGettingArchiveCaseId = Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .orgId("orgId")
                                .service("service")
                                .sourceApplication("testSourceApplication1")
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                .correlationId("testCorrelationId1")
                                .instanceId("testInstanceId1")
                                .configurationId("testConfigurationId1")
                                .caseId("testCaseId1")
                                .dispatchId("testDispatchId1")
                                .archiveCaseFolderId("testArchiveCaseFolderId1")
                                .build()
                )
                .name("case-dispatched")
                .type(EventType.INFO)
                .timestamp(LocalDateTime.of(2001, 1, 1, 12, 30))
                .build()
    }

    def "should return archiveCaseFolderId from event that is of type INFO, is a case dispatched event, and has matching source application and source application instance ids"() {
        given:
        eventRepository.save(eventApplicableForGettingArchiveCaseId)

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseFolderId(
                "testSourceApplication1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isPresent()
        archiveCaseId.get() == "testArchiveCaseFolderId1"
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
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseFolderId(
                "testSourceApplication1",
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
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseFolderId(
                "testSourceApplication1",
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
                                        .sourceApplication("testSourceApplication2")
                                        .build()
                        )
                        .build()
        )

        when:
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseFolderId(
                "testSourceApplication1",
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
        Optional<String> archiveCaseId = eventRepository.findArchiveCaseFolderId(
                "testSourceApplication1",
                "testSourceApplicationInstanceId1"
        )

        then:
        archiveCaseId.isEmpty()
    }

}
