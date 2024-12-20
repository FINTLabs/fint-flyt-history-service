package no.fintlabs.model.eventinfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.model.instance.InstanceStorageStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum InstanceStorageStatusEvent implements EventInfo {

    INSTANCE_REGISTERED("instance-registered",
            EventType.INFO, InstanceStorageStatus.STORED),

    INSTANCE_DELETED("instance-deleted",
            EventType.INFO, InstanceStorageStatus.STORED_AND_DELETED);

    private final String name;
    private final EventType type;
    private final InstanceStorageStatus storageStatus;

    public static Set<String> getAllEventNames() {
        return Arrays.stream(InstanceStorageStatusEvent.values())
                .map(InstanceStorageStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllEventNames(Collection<InstanceStorageStatus> statuses) {
        return Arrays.stream(InstanceStorageStatusEvent.values())
                .filter(e -> statuses.contains(e.storageStatus))
                .map(InstanceStorageStatusEvent::getName)
                .collect(Collectors.toSet());
    }

    private static final Map<String, InstanceStorageStatusEvent> valueByName = Arrays.stream(InstanceStorageStatusEvent.values())
            .collect(Collectors.toMap(InstanceStorageStatusEvent::getName, Function.identity()));

    public static InstanceStorageStatusEvent valueByName(String name) {
        return valueByName.get(name);
    }

}
