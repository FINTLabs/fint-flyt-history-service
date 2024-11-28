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

//    public Statistics getStatistics() {
//        return Statistics
//                .builder()
//                .dispatchedInstances(eventRepository.countDispatchedInstances())
//                .currentErrors(eventRepository.countCurrentInstanceErrors())
//                .build();
//    }

    public Statistics getStatistics(List<Long> sourceApplicationIds) {
        return Statistics
                .builder()
                .dispatchedInstances(eventRepository.countDispatchedInstancesBySourceApplicationIds(sourceApplicationIds))
                .currentErrors(eventRepository.countCurrentInstanceErrorsBySourceApplicationIds(sourceApplicationIds))
                .build();
    }

//    public List<IntegrationStatistics> getIntegrationStatistics() {
//        Map<String, Long> numberOfDispatchedInstancesPerIntegrationId = eventRepository.countDispatchedInstancesPerIntegrationId()
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        Map<String, Long> numberOfCurrentErrorsPerIntegrationId = eventRepository.countCurrentInstanceErrorsPerIntegrationId()
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        Map<String, Long> numberOfIntancesPerIntegrationId = eventRepository.countAllInstancesPerIntegrationId()
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        return getIntegrationStatistics(numberOfDispatchedInstancesPerIntegrationId, numberOfCurrentErrorsPerIntegrationId, numberOfIntancesPerIntegrationId);
//    }

    public Page<IntegrationStatistics> getIntegrationStatistics(
            Set<Long> userAuthorizationSourceApplicationIds,
            Set<Long> filterSourceApplicationIds,
            Set<String> filterSourceApplicationIntegrationIds,
            Set<Long> filterIntegrationIds,
            Pageable pageable
    ) {
        Set<Long> intersectedAuthorizationAndFilterSourceApplicationIds =
                intersectAuthorizationAndFilterSourceApplicationIds(userAuthorizationSourceApplicationIds, filterSourceApplicationIds);

//
//        EventRepository.QueryFilter queryFilter = EventRepository.QueryFilter
//                .builder()
//                .sourceApplicationIds(intersectedAuthorizationAndFilterSourceApplicationIds)
//                .sourceApplicationIntegrationIds(filterSourceApplicationIntegrationIds)
//                .integrationIds(filterIntegrationIds)
//                .build();

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


//    public List<IntegrationStatistics> getIntegrationStatisticsBySourceApplicationId(List<Long> sourceApplicationIds) {
//        Map<String, Long> numberOfDispatchedInstancesPerIntegrationId = eventRepository
//                .countDispatchedInstancesPerIntegrationIdBySourceApplicationIds(sourceApplicationIds)
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        Collection<Event> dispathedInstancesPerIntegrationIdBySourceApplicationIds =
//                eventRepository.findDispathedInstancesPerIntegrationIdBySourceApplicationIds(INSTANCE_DISPATCHED, sourceApplicationIds);
//
//        Map<String, Long> numberOfDispatchedInstancesPerIntegrationId =
//
//
//        Map<String, Long> numberOfCurrentErrorsPerIntegrationId = eventRepository
//                .countCurrentInstanceErrorsPerIntegrationIdBySourceApplicationIds(sourceApplicationIds)
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        Map<String, Long> numberOfIntancesPerIntegrationId = eventRepository
//                .countAllInstancesPerIntegrationIdBySourceApplicationIds(sourceApplicationIds)
//                .stream()
//                .collect(Collectors.toMap(
//                        EventRepository.IntegrationIdAndCount::getIntegrationId,
//                        EventRepository.IntegrationIdAndCount::getCount
//                ));
//
//        return getIntegrationStatistics(numberOfDispatchedInstancesPerIntegrationId, numberOfCurrentErrorsPerIntegrationId, numberOfIntancesPerIntegrationId);
//    }

//    @NotNull
//    private List<IntegrationStatistics> getIntegrationStatistics(
//            Map<String, Long> numberOfDispatchedInstancesPerIntegrationId,
//            Map<String, Long> numberOfCurrentErrorsPerIntegrationId,
//            Map<String, Long> numberOfIntancesPerIntegrationId
//    ) {
//        Set<String> integrationIds = Stream.concat(
//                        numberOfDispatchedInstancesPerIntegrationId.keySet().stream(),
//                        numberOfCurrentErrorsPerIntegrationId.keySet().stream()
//                )
//                .collect(Collectors.toSet());
//
//        return integrationIds
//                .stream()
//                .map(
//                        integrationId -> IntegrationStatistics
//                                .builder()
//                                .sourceApplicationIntegrationId(integrationId)
//                                .dispatchedInstances(numberOfDispatchedInstancesPerIntegrationId.getOrDefault(integrationId, 0L))
//                                .currentErrors(numberOfCurrentErrorsPerIntegrationId.getOrDefault(integrationId, 0L))
//                                .totalInstances(numberOfIntancesPerIntegrationId.getOrDefault(integrationId, 0L))
//                                .build()
//                )
//                .toList();
//    }

}
