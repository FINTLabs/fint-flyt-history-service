package no.novari.flyt.history.model.time;

import jakarta.validation.Valid;
import lombok.*;
import no.novari.flyt.history.model.instance.ActiveTimePeriod;
import no.novari.flyt.history.validation.OnlyOneTimeFilterType;

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
