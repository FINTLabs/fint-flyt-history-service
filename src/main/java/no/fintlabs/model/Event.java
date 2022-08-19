package no.fintlabs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;

@lombok.Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events")
@Builder(toBuilder = true)
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

    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private EventType type;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id")
    private Collection<Error> errors;

}
