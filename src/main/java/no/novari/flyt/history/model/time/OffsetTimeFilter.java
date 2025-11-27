package no.novari.flyt.history.model.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffsetTimeFilter {

    @JsonProperty
    @PositiveOrZero
    private Integer hours;

    @JsonProperty
    @PositiveOrZero
    private Integer minutes;

}
