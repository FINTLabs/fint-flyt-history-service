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
            null,
            true
    ),
    INSTANCE_REGISTERED(
            "instance-registered",
            EventType.INFO,
            null,
            InstanceStorageStatus.STORED,
            true
    ),
    INSTANCE_REQUESTED_FOR_RETRY(
            "instance-requested-for-retry",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null,
            true
    ),
    INSTANCE_MAPPED(
            "instance-mapped",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null,
            true
    ),
    INSTANCE_READY_FOR_DISPATCH(
            "instance-ready-for-dispatch",
            EventType.INFO,
            InstanceStatus.IN_PROGRESS,
            null,
            false
    ),
    INSTANCE_DISPATCHED(
            "instance-dispatched",
            EventType.INFO,
            InstanceStatus.TRANSFERRED,
            null,
            true
    ),
    INSTANCE_MANUALLY_PROCESSED(
            "instance-manually-processed",
            EventType.INFO,
            InstanceStatus.TRANSFERRED,
            null,
            false
    ),
    INSTANCE_MANUALLY_REJECTED(
            "instance-manually-rejected",
            EventType.INFO,
            InstanceStatus.ABORTED,
            null,
            false
    ),
    INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED(
            "instance-status-overridden-as-transferred",
            EventType.INFO,
            InstanceStatus.TRANSFERRED,
            null,
            false
    ),
    INSTANCE_RECEIVAL_ERROR(
            "instance-receival-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null,
            true
    ),
    INSTANCE_REGISTRATION_ERROR(
            "instance-registration-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null,
            true
    ),
    INSTANCE_RETRY_REQUEST_ERROR(
            "instance-retry-request-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null,
            true
    ),
    INSTANCE_MAPPING_ERROR(
            "instance-mapping-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null,
            true
    ),
    INSTANCE_DISPATCHING_ERROR(
            "instance-dispatching-error",
            EventType.ERROR,
            InstanceStatus.FAILED,
            null,
            true
    ),
    INSTANCE_DELETED(
            "instance-deleted",
            EventType.INFO,
            null,
            InstanceStorageStatus.STORED_AND_DELETED,
            true
    );

    private final String eventName;
    private final EventType type;
    private final InstanceStatus instanceStatus;
    private final InstanceStorageStatus instanceStorageStatus;
    private final boolean createKafkaListener;

}
