package no.fintlabs.model.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import no.fintlabs.model.instance.ActiveTimePeriod;

import javax.validation.constraints.NotNull;
import java.time.ZoneId;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveTimePeriodFilter {

    @JsonProperty
    @NotNull
    private ZoneId zoneId;

    @JsonProperty
    @NotNull
    private ActiveTimePeriod activeTimePeriod;
}
