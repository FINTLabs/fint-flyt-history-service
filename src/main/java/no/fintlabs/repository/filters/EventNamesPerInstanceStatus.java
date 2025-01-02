package no.fintlabs.repository.filters;

import lombok.Builder;
import lombok.Getter;

import java.util.Collection;

// TODO 20/12/2024 eivindmorch: Rename
@Getter
@Builder
public class EventNamesPerInstanceStatus {
    private final Collection<String> inProgressStatusEventNames;
    private final Collection<String> transferredStatusEventNames;
    private final Collection<String> rejectedStatusEventNames;
    private final Collection<String> failedStatusEventNames;
    private final Collection<String> allStatusEventNames;
}
