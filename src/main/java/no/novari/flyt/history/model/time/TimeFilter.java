package no.novari.flyt.history.model.time;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
