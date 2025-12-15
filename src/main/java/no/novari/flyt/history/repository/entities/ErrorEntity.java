package no.novari.flyt.history.repository.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

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

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "error_id_seq_gen"
    )
    @SequenceGenerator(
            name = "error_id_seq_gen",
            sequenceName = "error_id_seq",
            allocationSize = 500
    )
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
