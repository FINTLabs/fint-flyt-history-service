package no.fintlabs.repository.filters;

import lombok.Builder;
import lombok.Getter;

import java.util.Collection;

@Getter
@Builder
public class EventNamesPerInstanceStatus {
    private final Collection<String> inProgressStatusEventNames;
    private final Collection<String> transferredStatusEventNames;
    private final Collection<String> abortedStatusEventNames;
    private final Collection<String> failedStatusEventNames;
    private final Collection<String> allStatusEventNames;
}
