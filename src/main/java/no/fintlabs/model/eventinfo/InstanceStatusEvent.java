package no.fintlabs.model.eventinfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum InstanceStatusEvent implements EventInfo {

    INSTANCE_RECEIVED("instance-received",
            EventType.INFO, InstanceStatusEventCategory.IN_PROGRESS),

    INSTANCE_REQUESTED_FOR_RETRY("instance-requested-for-retry",
            EventType.INFO, InstanceStatusEventCategory.IN_PROGRESS),

    INSTANCE_MAPPED("instance-mapped",
            EventType.INFO, InstanceStatusEventCategory.IN_PROGRESS),

    INSTANCE_READY_FOR_DISPATCH("instance-ready-for-dispatch",
            EventType.INFO, InstanceStatusEventCategory.IN_PROGRESS),

    INSTANCE_DISPATCHED("instance-dispatched",
            EventType.INFO, InstanceStatusEventCategory.AUTOMATICALLY_DISPATCHED),

    INSTANCE_MANUALLY_PROCESSED("instance-manually-processed",
            EventType.INFO, InstanceStatusEventCategory.MANUALLY_PROCESSED),

    INSTANCE_MANUALLY_REJECTED("instance-manually-rejected",
            EventType.INFO, InstanceStatusEventCategory.MANUALLY_REJECTED),

    INSTANCE_RECEIVAL_ERROR("instance-receival-error",
            EventType.ERROR, InstanceStatusEventCategory.FAILED),

    INSTANCE_REGISTRATION_ERROR("instance-registration-error",
            EventType.ERROR, InstanceStatusEventCategory.FAILED),

    INSTANCE_RETRY_REQUEST_ERROR("instance-retry-request-error",
            EventType.ERROR, InstanceStatusEventCategory.FAILED),

    INSTANCE_MAPPING_ERROR("instance-mapping-error",
            EventType.ERROR, InstanceStatusEventCategory.FAILED),

    INSTANCE_DISPATCHING_ERROR("instance-dispatching-error",
            EventType.ERROR, InstanceStatusEventCategory.FAILED);

    private final String name;
    private final EventType type;
    private final InstanceStatusEventCategory category;

    public static Set<String> getAllEventNames() {
        return Arrays.stream(InstanceStatusEvent.values())
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllEventNames(InstanceStatusEventCategory category) {
        return Arrays.stream(InstanceStatusEvent.values())
                .filter(e -> e.getCategory() == category)
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllEventNames(InstanceStatusEventCategory... categories) {
        return getAllEventNames(Arrays.stream(categories).toList());
    }

    public static Set<String> getAllEventNames(Collection<InstanceStatusEventCategory> categories) {
        return Arrays.stream(InstanceStatusEvent.values())
                .filter(e -> categories.contains(e.getCategory()))
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

}
