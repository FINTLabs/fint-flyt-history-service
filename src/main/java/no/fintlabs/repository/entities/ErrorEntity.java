package no.fintlabs.repository.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.persistence.*;
import java.util.Map;

@Getter
@Setter
@Jacksonized
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "error")
public class ErrorEntity {

    // TODO 20/12/2024 eivindmorch: Identity or sequence? How is migration done?
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private long id;

    private String errorCode;

    @ElementCollection
    @CollectionTable(
            name = "error_args",
            joinColumns = {@JoinColumn(name = "error_id", referencedColumnName = "id")}
    )
    @MapKeyColumn(name = "map_key")
    @Column(name = "\"value\"", columnDefinition = "text")
    @JsonPropertyOrder(alphabetic = true)
    private Map<String, String> args;

}
