package no.fintlabs.model.time;

import lombok.*;
import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.validation.OnlyOneTimeFilterType;

import javax.validation.Valid;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@OnlyOneTimeFilterType
public class TimeFilter {

    @Valid
    private OffsetTimeFilter offset;

    private ActiveTimePeriod currentPeriod;

    @Valid
    private ManualTimeFilter manual;

}
