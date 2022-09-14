package no.fintlabs

import no.fintlabs.model.IntegrationStatistics
import no.fintlabs.repositories.EventRepository
import spock.lang.Specification

import javax.persistence.Tuple

class IntegrationStatisticsServiceSpec extends Specification {

    EventRepository eventRepository
    IntegrationStatisticsService integrationStatisticsService

    private Tuple createTuple(String v1, Long v2) {
        Tuple tuple = Mock(Tuple.class)
        tuple.get(0, String.class) >> v1
        tuple.get(1, Long.class) >> v2
        return tuple
    }

    def setup() {
        eventRepository = Mock(EventRepository.class)
        integrationStatisticsService = new IntegrationStatisticsService(eventRepository)
    }

    def 'asd'() {
        given:
        eventRepository.findNumberOfDispatchedInstancesPerIntegrationId() >> List.of(
                createTuple("1", 27),
                createTuple("3", 102),
        )

        eventRepository.findNumberOfCurrentInstanceErrorsPerIntegrationId() >> List.of(
                createTuple("3", 24),
                createTuple("4", 1),
        )

        when:
        Collection<IntegrationStatistics> integrationStatistics = integrationStatisticsService.getIntegrationStatistics()

        then:
        integrationStatistics.size() == 3
        integrationStatistics.containsAll(
                IntegrationStatistics.builder().sourceApplicationIntegrationId("1").dispatchedInstances(27).currentErrors(0).build(),
                IntegrationStatistics.builder().sourceApplicationIntegrationId("3").dispatchedInstances(102).currentErrors(24).build(),
                IntegrationStatistics.builder().sourceApplicationIntegrationId("4").dispatchedInstances(0).currentErrors(1).build(),
        )
    }

}
