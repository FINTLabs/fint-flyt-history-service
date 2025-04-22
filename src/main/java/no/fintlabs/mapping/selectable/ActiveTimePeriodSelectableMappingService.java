package no.fintlabs.mapping.selectable;

import no.fintlabs.model.Selectable;
import no.fintlabs.model.instance.ActiveTimePeriod;
import org.springframework.stereotype.Service;

@Service
public class ActiveTimePeriodSelectableMappingService {

    public Selectable<String> toSelectable(ActiveTimePeriod activeTimePeriod) {
        return Selectable
                .<String>builder()
                .value(activeTimePeriod.name())
                .label(getDisplayText(activeTimePeriod))
                .build();
    }

    private String getDisplayText(ActiveTimePeriod activeTimePeriod) {
        return switch (activeTimePeriod) {
            case TODAY -> "Denne dagen";
            case THIS_WEEK -> "Denne uka";
            case THIS_MONTH -> "Denne måneden";
            case THIS_YEAR -> "Dette året";
        };
    }

}
