package no.novari.flyt.history.model.event;

import lombok.Getter;
import no.novari.flyt.history.model.instance.InstanceStatus;
import no.novari.flyt.history.model.instance.InstanceStorageStatus;
import no.novari.flyt.history.repository.filters.EventNamesPerInstanceStatus;
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
                .map(EventCategory::getEventName)
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
                .map(EventCategory::getEventName)
                .collect(Collectors.toSet());
    }

    private final Map<String, EventCategory> categoryByName = Arrays.stream(EventCategory.values())
            .collect(Collectors.toMap(EventCategory::getEventName, Function.identity()));

    public EventCategory getCategoryByEventName(String name) {
        return categoryByName.get(name);
    }

    public InstanceStatus getStatusByEventName(String name) {
        EventCategory category = getCategoryByEventName(name);
        if (category == null) {
            throw new IllegalArgumentException("No category with name=" + name);
        }
        InstanceStatus status = category.getInstanceStatus();
        if (status == null) {
            throw new IllegalArgumentException("Category=" + name + " is not an instance status category");
        }
        return status;
    }

    public InstanceStorageStatus getStorageStatusByEventName(String name) {
        EventCategory category = getCategoryByEventName(name);
        if (category == null) {
            throw new IllegalArgumentException("No category with name=" + name);
        }
        InstanceStorageStatus status = category.getInstanceStorageStatus();
        if (status == null) {
            throw new IllegalArgumentException("Category=" + name + " is not an instance storage status category");
        }
        return status;
    }

    public Set<EventCategory> getInstanceStatusCategories() {
        return Arrays.stream(EventCategory.values())
                .filter(eventCategory -> Objects.nonNull(eventCategory.getInstanceStatus()))
                .collect(Collectors.toSet());
    }

    public Set<String> getAllInstanceStatusEventNames() {
        return getInstanceStatusCategories()
                .stream()
                .map(EventCategory::getEventName)
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
                .map(EventCategory::getEventName)
                .collect(Collectors.toSet());
    }

}
