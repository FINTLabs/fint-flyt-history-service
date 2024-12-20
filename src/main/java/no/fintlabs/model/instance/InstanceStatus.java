package no.fintlabs.model.instance;

public enum InstanceStatus {
    IN_PROGRESS,
    TRANSFERRED, // TODO 20/12/2024 eivindmorch: Rename to successful?
    REJECTED, // TODO 20/12/2024 eivindmorch: Rename to cancelled or aborted?
    FAILED
}
