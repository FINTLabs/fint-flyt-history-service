package no.fintlabs.model.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.model.instance.InstanceStatus;
import no.fintlabs.model.instance.InstanceStorageStatus;

@Getter
@AllArgsConstructor
public enum EventCategory {
    INSTANCE_RECEIVED(
            "instance-received",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null
    ),
    INSTANCE_REGISTERED(
            "instance-registered",
            EventType.INFO,
            null,
            InstanceStorageStatus.STORED
    ),
    INSTANCE_REQUESTED_FOR_RETRY(
            "instance-requested-for-retry",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null
    ),
    INSTANCE_MAPPED(
            "instance-mapped",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null
    ),
    INSTANCE_READY_FOR_DISPATCH(
            "instance-ready-for-dispatch",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null
    ),
    INSTANCE_DISPATCHED(
            "instance-dispatched",
            EventType.INFO,
            InstanceStatus.TRANSFERRED,
            null
    ),
    INSTANCE_MANUALLY_PROCESSED(
            "instance-manually-processed",
            EventType.INFO,
            InstanceStatus.TRANSFERRED,
            null
    ),
    INSTANCE_MANUALLY_REJECTED(
            "instance-manually-rejected",
            EventType.INFO,
            InstanceStatus.ABORTED,
            null
    ),
    INSTANCE_RECEIVAL_ERROR(
            "instance-receival-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null
    ),
    INSTANCE_REGISTRATION_ERROR(
            "instance-registration-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null
    ),
    INSTANCE_RETRY_REQUEST_ERROR(
            "instance-retry-request-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null
    ),
    INSTANCE_MAPPING_ERROR(
            "instance-mapping-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null
    ),
    INSTANCE_DISPATCHING_ERROR(
            "instance-dispatching-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null
    ),
    INSTANCE_DELETED(
            "instance-deleted",
            EventType.INFO,
            null,
            InstanceStorageStatus.STORED_AND_DELETED
    );

    private final String name;
    private final EventType type;
    private final InstanceStatus instanceStatus;
    private final InstanceStorageStatus instanceStorageStatus;

}
