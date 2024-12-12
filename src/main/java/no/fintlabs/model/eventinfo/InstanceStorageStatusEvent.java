package no.fintlabs.model.eventinfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum InstanceStorageStatusEvent implements EventInfo {

    INSTANCE_REGISTERED("instance-registered", EventType.INFO),

    INSTANCE_DELETED("instance-deleted", EventType.INFO);

    private final String name;
    private final EventType type;

    public static Set<String> getAllEventNames() {
        return Arrays.stream(InstanceStorageStatusEvent.values())
                .map(InstanceStorageStatusEvent::getName)
                .collect(Collectors.toSet());
    }

}
