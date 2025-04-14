package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InstanceFlowSummariesFilterMappingService {

    private final Validator validator;
    private final EventCategorizationService eventCategorizationService;
    private final TimeFilterMappingService timeFilterMappingService;

    public InstanceFlowSummariesFilterMappingService(
            Validator validator,
            EventCategorizationService eventCategorizationService,
            TimeFilterMappingService timeFilterMappingService

    ) {
        this.validator = validator;
        this.eventCategorizationService = eventCategorizationService;
        this.timeFilterMappingService = timeFilterMappingService;
    }

    public InstanceFlowSummariesQueryFilter toQueryFilter(InstanceFlowSummariesFilter instanceFlowSummariesFilter) {
        if (instanceFlowSummariesFilter == null) {
            throw new IllegalArgumentException("Instance flow summaries filter is null");
        }
        Set<ConstraintViolation<InstanceFlowSummariesFilter>> validate = validator.validate(instanceFlowSummariesFilter);
        if (!validate.isEmpty()) {
            throw new IllegalArgumentException("Invalid instance flow summaries filter: " + validate);
        }
        return InstanceFlowSummariesQueryFilter
                .builder()
                .sourceApplicationIds(instanceFlowSummariesFilter.getSourceApplicationIds())
                .sourceApplicationIntegrationIds(instanceFlowSummariesFilter.getSourceApplicationIntegrationIds())
                .sourceApplicationInstanceIds(instanceFlowSummariesFilter.getSourceApplicationInstanceIds())
                .integrationIds(instanceFlowSummariesFilter.getIntegrationIds())
                .statusEventNames(
                        Optional.ofNullable(instanceFlowSummariesFilter.getStatuses())
                                .map(eventCategorizationService::getEventNamesByInstanceStatuses)
                                .or(() -> Optional.ofNullable(instanceFlowSummariesFilter.getLatestStatusEvents())
                                        .map(lse -> lse.stream()
                                                .map(EventCategory::getEventName)
                                                .collect(Collectors.toSet())
                                        )
                                ).orElse(null)
                )
                .instanceStorageStatusQueryFilter(
                        Optional.ofNullable(instanceFlowSummariesFilter.getStorageStatuses())
                                .map(storageStatuses -> new InstanceStorageStatusQueryFilter(
                                        eventCategorizationService.getEventNamesByInstanceStorageStatuses(
                                                storageStatuses
                                        ),
                                        storageStatuses.contains(InstanceStorageStatus.NEVER_STORED)
                                )).orElse(InstanceStorageStatusQueryFilter.EMPTY)
                )
                .associatedEventNames(
                        Optional.ofNullable(instanceFlowSummariesFilter.getAssociatedEvents())
                                .map(eventCategories -> eventCategories.stream()
                                        .map(EventCategory::getEventName)
                                        .toList()
                                ).orElse(null)
                )
                .destinationIds(instanceFlowSummariesFilter.getDestinationIds())
                .timeQueryFilter(
                        Optional.ofNullable(instanceFlowSummariesFilter.getTime())
                                .map(timeFilter -> timeFilterMappingService.toQueryFilter(
                                        timeFilter,
                                        instanceFlowSummariesFilter.getTimeZone()
                                ))
                                .orElse(null)
                )
                .build();
    }

}
