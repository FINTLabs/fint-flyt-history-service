package no.novari.flyt.history.model.event

import no.novari.flyt.history.model.instance.InstanceStatus
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import kotlin.jvm.JvmName

enum class EventCategory(
    val eventName: String,
    val type: EventType,
    val instanceStatus: InstanceStatus?,
    val instanceStorageStatus: InstanceStorageStatus?,
    @get:JvmName("isCreateKafkaListener")
    val createKafkaListener: Boolean,
) {
    INSTANCE_RECEIVED(
        eventName = "instance-received",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.IN_PROGRESS,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_REGISTERED(
        eventName = "instance-registered",
        type = EventType.INFO,
        instanceStatus = null,
        instanceStorageStatus = InstanceStorageStatus.STORED,
        createKafkaListener = true,
    ),
    INSTANCE_REQUESTED_FOR_RETRY(
        eventName = "instance-requested-for-retry",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.IN_PROGRESS,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_MAPPED(
        eventName = "instance-mapped",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.IN_PROGRESS,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_READY_FOR_DISPATCH(
        eventName = "instance-ready-for-dispatch",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.IN_PROGRESS,
        instanceStorageStatus = null,
        createKafkaListener = false,
    ),
    INSTANCE_DISPATCHED(
        eventName = "instance-dispatched",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.TRANSFERRED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_MANUALLY_PROCESSED(
        eventName = "instance-manually-processed",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.TRANSFERRED,
        instanceStorageStatus = null,
        createKafkaListener = false,
    ),
    INSTANCE_MANUALLY_REJECTED(
        eventName = "instance-manually-rejected",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.ABORTED,
        instanceStorageStatus = null,
        createKafkaListener = false,
    ),
    INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED(
        eventName = "instance-status-overridden-as-transferred",
        type = EventType.INFO,
        instanceStatus = InstanceStatus.TRANSFERRED,
        instanceStorageStatus = null,
        createKafkaListener = false,
    ),
    INSTANCE_RECEIVAL_ERROR(
        eventName = "instance-receival-error",
        type = EventType.ERROR,
        instanceStatus = InstanceStatus.FAILED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_REGISTRATION_ERROR(
        eventName = "instance-registration-error",
        type = EventType.ERROR,
        instanceStatus = InstanceStatus.FAILED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_RETRY_REQUEST_ERROR(
        eventName = "instance-retry-request-error",
        type = EventType.ERROR,
        instanceStatus = InstanceStatus.FAILED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_MAPPING_ERROR(
        eventName = "instance-mapping-error",
        type = EventType.ERROR,
        instanceStatus = InstanceStatus.FAILED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_DISPATCHING_ERROR(
        eventName = "instance-dispatching-error",
        type = EventType.ERROR,
        instanceStatus = InstanceStatus.FAILED,
        instanceStorageStatus = null,
        createKafkaListener = true,
    ),
    INSTANCE_DELETED(
        eventName = "instance-deleted",
        type = EventType.INFO,
        instanceStatus = null,
        instanceStorageStatus = InstanceStorageStatus.STORED_AND_DELETED,
        createKafkaListener = true,
    ),
}
