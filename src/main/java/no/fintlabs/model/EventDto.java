package no.fintlabs.model;

import lombok.*;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;

import java.time.OffsetDateTime;
import java.util.Collection;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
public class EventDto {
    private InstanceFlowHeaders instanceFlowHeaders;
    private String name;
    private OffsetDateTime timestamp;
    private EventType type;
    private String applicationId;
    private Collection<Error> errors;

    public EventDto() {
    }
}
