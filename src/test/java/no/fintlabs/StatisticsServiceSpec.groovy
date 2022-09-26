package no.fintlabs

import no.fintlabs.model.IntegrationStatistics
import no.fintlabs.model.Statistics
import no.fintlabs.repositories.EventRepository
import spock.lang.Specification

import javax.persistence.Tuple

class StatisticsServiceSpec extends Specification {

    EventRepository eventRepository
    StatisticsService statisticsService

    private Tuple createTuple(String v1, Long v2) {
        Tuple tuple = Mock(Tuple.class)
        tuple.get(0, String.class) >> v1
        tuple.get(1, Long.class) >> v2
        return tuple
    }

    def setup() {
        eventRepository = Mock(EventRepository.class)
        statisticsService = new StatisticsService(eventRepository)
    }

    def 'should map to statistics'() {
        given:
        eventRepository.countDispatchedInstances() >> 184
        eventRepository.countCurrentInstanceErrors() >> 3

        when:
        Statistics statistics = statisticsService.getStatistics()

        then:
        statistics == Statistics
                .builder()
                .dispatchedInstances(184)
                .currentErrors(3)
                .build()
    }

    def 'should map to integration statistics'() {
        given:
        eventRepository.countDispatchedInstancesPerIntegrationId() >> List.of(
                new EventRepository.IntegrationIdAndCount() {
                    @Override
                    String getIntegrationId() {
                        return "1"
                    }

                    @Override
                    long getCount() {
                        return 27
                    }
                },
                new EventRepository.IntegrationIdAndCount() {
                    @Override
                    String getIntegrationId() {
                        return "3"
                    }

                    @Override
                    long getCount() {
                        return 102
                    }
                }
        )

        eventRepository.countCurrentInstanceErrorsPerIntegrationId() >> List.of(
                new EventRepository.IntegrationIdAndCount() {
                    @Override
                    String getIntegrationId() {
                        return "3"
                    }

                    @Override
                    long getCount() {
                        return 24
                    }
                },
                new EventRepository.IntegrationIdAndCount() {
                    @Override
                    String getIntegrationId() {
                        return "4"
                    }

                    @Override
                    long getCount() {
                        return 1
                    }
                }
        )

        when:
        Collection<IntegrationStatistics> integrationStatistics = statisticsService.getIntegrationStatistics()

        then:
        integrationStatistics.size() == 3
        integrationStatistics.containsAll(
                IntegrationStatistics.builder().sourceApplicationIntegrationId("1").dispatchedInstances(27).currentErrors(0).build(),
                IntegrationStatistics.builder().sourceApplicationIntegrationId("3").dispatchedInstances(102).currentErrors(24).build(),
                IntegrationStatistics.builder().sourceApplicationIntegrationId("4").dispatchedInstances(0).currentErrors(1).build(),
        )
    }

}
