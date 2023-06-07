package no.fintlabs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.Collection;

@Getter
@Setter
@EqualsAndHashCode
@Jacksonized
@Entity
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long id;

    @Embedded
    private InstanceFlowHeadersEmbeddable instanceFlowHeaders;

    private String name;

    private OffsetDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String applicationId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id")
    private Collection<Error> errors;

}
