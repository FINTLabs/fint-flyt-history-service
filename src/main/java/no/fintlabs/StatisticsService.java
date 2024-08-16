package no.fintlabs;

import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.model.Statistics;
import no.fintlabs.repositories.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StatisticsService {

    private final EventRepository eventRepository;

    public StatisticsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Statistics getStatistics() {
        return Statistics
                .builder()
                .dispatchedInstances(eventRepository.countDispatchedInstances())
                .currentErrors(eventRepository.countCurrentInstanceErrors())
                .build();
    }

    public Statistics getStatisticsBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return Statistics
                .builder()
                .dispatchedInstances(eventRepository.countDispatchedInstancesBySourceApplicationIds(sourceApplicationIds))
                .currentErrors(eventRepository.countCurrentInstanceErrors())
                .build();
    }

    public List<IntegrationStatistics> getIntegrationStatistics() {
        Map<String, Long> numberOfDispatchedInstancesPerIntegrationId = eventRepository.countDispatchedInstancesPerIntegrationId()
                .stream()
                .collect(Collectors.toMap(
                        EventRepository.IntegrationIdAndCount::getIntegrationId,
                        EventRepository.IntegrationIdAndCount::getCount
                ));

        Map<String, Long> numberOfCurrentErrorsPerIntegrationId = eventRepository.countCurrentInstanceErrorsPerIntegrationId()
                .stream()
                .collect(Collectors.toMap(
                        EventRepository.IntegrationIdAndCount::getIntegrationId,
                        EventRepository.IntegrationIdAndCount::getCount
                ));

        Set<String> integrationIds = Stream.concat(
                        numberOfDispatchedInstancesPerIntegrationId.keySet().stream(),
                        numberOfCurrentErrorsPerIntegrationId.keySet().stream()
                )
                .collect(Collectors.toSet());

        return integrationIds
                .stream()
                .map(
                        integrationId -> IntegrationStatistics
                                .builder()
                                .sourceApplicationIntegrationId(integrationId)
                                .dispatchedInstances(numberOfDispatchedInstancesPerIntegrationId.getOrDefault(integrationId, 0L))
                                .currentErrors(numberOfCurrentErrorsPerIntegrationId.getOrDefault(integrationId, 0L))
                                .build()
                )
                .toList();
    }

}
