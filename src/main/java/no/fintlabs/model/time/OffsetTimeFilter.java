package no.fintlabs.model.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.constraints.PositiveOrZero;

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
