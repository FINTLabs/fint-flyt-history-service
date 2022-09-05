package no.fintlabs;

import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.model.IntegrationStatisticsWrapper;
import no.fintlabs.repositories.EventRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class IntegrationStatisticsService {

    private final EventRepository eventRepository;

    public IntegrationStatisticsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public IntegrationStatisticsWrapper getIntegrationStatistics() {
        Map<String, Long> numberOfDispatchedInstancesPerIntegrationId = eventRepository.findNumberOfDispatchedInstancesPerIntegrationId()
                .stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(0, String.class),
                        tuple -> tuple.get(1, Long.class)
                ));

        Map<String, Long> numberOfCurrentErrorsPerIntegrationId = eventRepository.findNumberOfCurrentInstanceErrorsPerIntegrationId()
                .stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(0, String.class),
                        tuple -> tuple.get(1, Long.class)
                ));

        Set<String> integrationIds = Stream.concat(
                        numberOfDispatchedInstancesPerIntegrationId.keySet().stream(),
                        numberOfCurrentErrorsPerIntegrationId.keySet().stream()
                )
                .collect(Collectors.toSet());

        Map<String, IntegrationStatistics> statisticsPerIntegrationId = integrationIds
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        integrationId -> IntegrationStatistics
                                .builder()
                                .dispatchedInstances(numberOfDispatchedInstancesPerIntegrationId.getOrDefault(integrationId, 0L))
                                .currentErrors(numberOfCurrentErrorsPerIntegrationId.getOrDefault(integrationId, 0L))
                                .build()
                ));

        return IntegrationStatisticsWrapper
                .builder()
                .statisticsPerIntegrationId(statisticsPerIntegrationId)
                .build();
    }
}
