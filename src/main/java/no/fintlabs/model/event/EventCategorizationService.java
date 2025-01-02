package no.fintlabs.model.event;

import lombok.Getter;
import no.fintlabs.model.instance.InstanceStatus;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.filters.EventNamesPerInstanceStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Service
public class EventCategorizationService {

    private final EventNamesPerInstanceStatus eventNamesPerInstanceStatus;

    public EventCategorizationService() {
        eventNamesPerInstanceStatus = EventNamesPerInstanceStatus
                .builder()
                .inProgressStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.IN_PROGRESS))
                .transferredStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.TRANSFERRED))
                .abortedStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.ABORTED))
                .failedStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.FAILED))
                .allStatusEventNames(getAllInstanceStatusEventNames())
                .build();
    }

    public Set<EventCategory> getCategoriesByInstanceStorageStatuses(Collection<InstanceStorageStatus> instanceStorageStatuses) {
        return Arrays.stream(EventCategory.values())
                .filter(eventCategory -> instanceStorageStatuses.contains(eventCategory.getInstanceStorageStatus()))
                .collect(Collectors.toSet());
    }

    public Set<String> getEventNamesByInstanceStorageStatuses(Collection<InstanceStorageStatus> instanceStorageStatuses) {
        return getCategoriesByInstanceStorageStatuses(instanceStorageStatuses)
                .stream()
                .map(EventCategory::getName)
                .collect(Collectors.toSet());
    }

    public Set<EventCategory> getCategoriesByInstanceStatuses(Collection<InstanceStatus> instanceStatuses) {
        return Arrays.stream(EventCategory.values())
                .filter(eventCategory -> instanceStatuses.contains(eventCategory.getInstanceStatus()))
                .collect(Collectors.toSet());
    }

    public Set<String> getEventNamesByInstanceStatuses(InstanceStatus... instanceStatuses) {
        return getEventNamesByInstanceStatuses(Arrays.asList(instanceStatuses));
    }

    public Set<String> getEventNamesByInstanceStatuses(Collection<InstanceStatus> instanceStatuses) {
        return getCategoriesByInstanceStatuses(instanceStatuses)
                .stream()
                .map(EventCategory::getName)
                .collect(Collectors.toSet());
    }

    private final Map<String, EventCategory> categoryByName = Arrays.stream(EventCategory.values())
            .collect(Collectors.toMap(EventCategory::getName, Function.identity()));

    public EventCategory getCategoryByName(String name) {
        return categoryByName.get(name);
    }

    public Set<EventCategory> getInstanceStatusCategories() {
        return Arrays.stream(EventCategory.values())
                .filter(eventCategory -> Objects.nonNull(eventCategory.getInstanceStatus()))
                .collect(Collectors.toSet());
    }

    public Set<String> getAllInstanceStatusEventNames() {
        return getInstanceStatusCategories()
                .stream()
                .map(EventCategory::getName)
                .collect(Collectors.toSet());
    }

    public Set<EventCategory> getInstanceStorageStatusCategories() {
        return Arrays.stream(EventCategory.values())
                .filter(eventCategory -> Objects.nonNull(eventCategory.getInstanceStorageStatus()))
                .collect(Collectors.toSet());
    }

    public Set<String> getAllInstanceStorageStatusEventNames() {
        return getInstanceStorageStatusCategories()
                .stream()
                .map(EventCategory::getName)
                .collect(Collectors.toSet());
    }

}
