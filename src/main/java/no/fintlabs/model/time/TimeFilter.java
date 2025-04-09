package no.fintlabs.model.time;

import lombok.*;
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

    @Valid
    private ActiveTimePeriodFilter currentPeriod;

    @Valid
    private ManualTimeFilter manual;

}
