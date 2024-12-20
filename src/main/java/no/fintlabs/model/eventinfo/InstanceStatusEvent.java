package no.fintlabs.model.eventinfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.model.instance.InstanceStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum InstanceStatusEvent implements EventInfo {
    INSTANCE_RECEIVED("instance-received",
            EventType.INFO, InstanceStatus.IN_PROGRESS),

    INSTANCE_REQUESTED_FOR_RETRY("instance-requested-for-retry",
            EventType.INFO, InstanceStatus.IN_PROGRESS),

    INSTANCE_MAPPED("instance-mapped",
            EventType.INFO, InstanceStatus.IN_PROGRESS),

    INSTANCE_READY_FOR_DISPATCH("instance-ready-for-dispatch",
            EventType.INFO, InstanceStatus.IN_PROGRESS),

    INSTANCE_DISPATCHED("instance-dispatched",
            EventType.INFO, InstanceStatus.TRANSFERRED),

    INSTANCE_MANUALLY_PROCESSED("instance-manually-processed",
            EventType.INFO, InstanceStatus.TRANSFERRED),

    INSTANCE_MANUALLY_REJECTED("instance-manually-rejected",
            EventType.INFO, InstanceStatus.REJECTED),

    INSTANCE_RECEIVAL_ERROR("instance-receival-error",
            EventType.ERROR, InstanceStatus.FAILED),

    INSTANCE_REGISTRATION_ERROR("instance-registration-error",
            EventType.ERROR, InstanceStatus.FAILED),

    INSTANCE_RETRY_REQUEST_ERROR("instance-retry-request-error",
            EventType.ERROR, InstanceStatus.FAILED),

    INSTANCE_MAPPING_ERROR("instance-mapping-error",
            EventType.ERROR, InstanceStatus.FAILED),

    INSTANCE_DISPATCHING_ERROR("instance-dispatching-error",
            EventType.ERROR, InstanceStatus.FAILED);

    private final String name;
    private final EventType type;
    private final InstanceStatus instanceStatus;

    public static Set<String> getAllEventNames() {
        return Arrays.stream(InstanceStatusEvent.values())
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllEventNames(InstanceStatus status) {
        return Arrays.stream(InstanceStatusEvent.values())
                .filter(e -> e.getInstanceStatus() == status)
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllEventNames(Collection<InstanceStatus> statuses) {
        return Arrays.stream(InstanceStatusEvent.values())
                .filter(e -> statuses.contains(e.getInstanceStatus()))
                .map(InstanceStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    private static final Map<String, InstanceStatusEvent> valueByName = Arrays.stream(InstanceStatusEvent.values())
            .collect(Collectors.toMap(InstanceStatusEvent::getName, Function.identity()));

    public static InstanceStatusEvent valueByName(String name) {
        return valueByName.get(name);
    }

}
