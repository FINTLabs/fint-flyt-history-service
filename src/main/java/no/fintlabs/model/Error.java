package no.fintlabs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode
@Jacksonized
@Entity
public class Error {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long id;

    private String errorCode;

    @ElementCollection
    @CollectionTable(
            name = "error_args",
            joinColumns = {@JoinColumn(name = "error_id", referencedColumnName = "id")}
    )
    @MapKeyColumn(name = "map_key")
    @Column(name = "value", columnDefinition = "text")
    @JsonPropertyOrder(alphabetic = true)
    private Map<String, String> args;

}
