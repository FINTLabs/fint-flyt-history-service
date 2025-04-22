package no.fintlabs.mapping.selectable;

import no.fintlabs.model.Selectable;
import no.fintlabs.model.instance.InstanceStorageStatus;
import org.springframework.stereotype.Service;

@Service
public class InstanceStorageStatusSelectableMappingService {

    public Selectable<String> toSelectable(InstanceStorageStatus instanceStorageStatus) {
        return Selectable
                .<String>builder()
                .value(instanceStorageStatus.name())
                .label(getDisplayText(instanceStorageStatus))
                .build();
    }

    private String getDisplayText(InstanceStorageStatus instanceStorageStatus) {
        return switch (instanceStorageStatus) {
            case STORED -> "Lagret";
            case STORED_AND_DELETED -> "Lagret og slettet";
            case NEVER_STORED -> "Aldri lagret";
        };
    }

}
