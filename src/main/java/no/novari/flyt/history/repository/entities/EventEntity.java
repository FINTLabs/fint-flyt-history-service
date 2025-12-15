package no.novari.flyt.history.repository.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import no.novari.flyt.history.model.event.EventType;

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
