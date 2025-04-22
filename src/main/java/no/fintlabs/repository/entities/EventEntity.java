package no.fintlabs.repository.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.event.EventType;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Jacksonized
@Entity
@Table(name = "event", indexes = {
        @Index(name = "timestamp_index", columnList = "timestamp"),
        @Index(
                name = "source_application_aggregate_id_and_timestamp_and_name_index",
                columnList = "sourceApplicationId, " +
                             "sourceApplicationIntegrationId, " +
                             "sourceApplicationInstanceId, " +
                             "timestamp," +
                             "name"
        ),
})
public class EventEntity {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "event_id_seq_gen"
    )
    @SequenceGenerator(
            name = "event_id_seq_gen",
            sequenceName = "event_id_seq",
            allocationSize = 500
    )
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private long id;

    @Embedded
    private InstanceFlowHeadersEmbeddable instanceFlowHeaders;

    private String name;

    private OffsetDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String applicationId;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id")
    private Collection<ErrorEntity> errors = new ArrayList<>();

}
