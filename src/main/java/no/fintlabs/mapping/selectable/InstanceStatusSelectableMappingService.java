package no.fintlabs.mapping.selectable;

import no.fintlabs.model.Selectable;
import no.fintlabs.model.instance.InstanceStatus;
import org.springframework.stereotype.Service;

@Service
public class InstanceStatusSelectableMappingService {

    public Selectable<String> toSelectable(InstanceStatus instanceStatus) {
        return Selectable
                .<String>builder()
                .value(instanceStatus.name())
                .label(getDisplayText(instanceStatus))
                .build();
    }

    public String getDisplayText(InstanceStatus instanceStatus) {
        return switch (instanceStatus) {
            case IN_PROGRESS -> "Under behandling";
            case TRANSFERRED -> "Overført";
            case ABORTED -> "Avbrutt";
            case FAILED -> "Feilet";
        };
    }

}
