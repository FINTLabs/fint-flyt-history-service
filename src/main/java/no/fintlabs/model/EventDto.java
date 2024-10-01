package no.fintlabs.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Collection;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
public class EventDto {
    private InstanceFlowHeadersEmbeddable instanceFlowHeaders;
    private String name;
    private OffsetDateTime timestamp;
    private EventType type;
    private String applicationId;
    private Collection<Error> errors;
    private String status;

    public EventDto() {
    }
}
