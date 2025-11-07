package no.fintlabs.model.time;

import jakarta.validation.Valid;
import lombok.*;
import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.validation.OnlyOneTimeFilterType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@OnlyOneTimeFilterType
public class TimeFilter {

    @Valid
    private OffsetTimeFilter offset;

    @Valid
    private ActiveTimePeriod currentPeriod;

    @Valid
    private ManualTimeFilter manual;

}
