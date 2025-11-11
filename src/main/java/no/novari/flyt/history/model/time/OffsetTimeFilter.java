package no.novari.flyt.history.model.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

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
