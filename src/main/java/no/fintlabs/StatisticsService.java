package no.fintlabs;

import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.model.Statistics;
import no.fintlabs.repositories.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StatisticsService {

    private final EventRepository eventRepository;

    public StatisticsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // TODO 04/12/2024 eivindmorch: Replace with query that doesnt include duplicate dispatches and errors
    public Statistics getStatistics(List<Long> sourceApplicationIds) {
        return Statistics
                .builder()
                .dispatchedInstances(eventRepository.countDispatchedInstancesBySourceApplicationIds(sourceApplicationIds))
                .currentErrors(eventRepository.countCurrentInstanceErrorsBySourceApplicationIds(sourceApplicationIds))
                .build();
    }

    public Page<IntegrationStatistics> getIntegrationStatistics(
            Set<Long> userAuthorizationSourceApplicationIds,
            Set<Long> filterSourceApplicationIds,
            Set<String> filterSourceApplicationIntegrationIds,
            Set<Long> filterIntegrationIds,
            Pageable pageable
    ) {
        Set<Long> intersectedAuthorizationAndFilterSourceApplicationIds =
                intersectAuthorizationAndFilterSourceApplicationIds(userAuthorizationSourceApplicationIds, filterSourceApplicationIds);

        return eventRepository.getIntegrationStatistics(
                intersectedAuthorizationAndFilterSourceApplicationIds,
                filterSourceApplicationIntegrationIds,
                filterIntegrationIds,
                pageable
        );
    }

    private Set<Long> intersectAuthorizationAndFilterSourceApplicationIds(
            Set<Long> userAuthorizationSourceApplicationIds,
            Set<Long> filterSourceApplicationIds
    ) {
        if (filterSourceApplicationIds == null) {
            return userAuthorizationSourceApplicationIds;
        }

        Set<Long> intersection = new HashSet<>(userAuthorizationSourceApplicationIds);
        intersection.retainAll(filterSourceApplicationIds);
        return intersection;
    }

}
