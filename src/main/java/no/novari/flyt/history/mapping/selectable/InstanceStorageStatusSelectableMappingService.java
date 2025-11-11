package no.novari.flyt.history.mapping.selectable;

import no.novari.flyt.history.model.Selectable;
import no.novari.flyt.history.model.instance.InstanceStorageStatus;
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
