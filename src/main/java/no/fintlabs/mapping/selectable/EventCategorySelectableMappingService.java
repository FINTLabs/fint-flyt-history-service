package no.fintlabs.mapping.selectable;

import no.fintlabs.model.Selectable;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceStatus;
import org.springframework.stereotype.Service;

@Service
public class EventCategorySelectableMappingService {

    private final InstanceStatusSelectableMappingService instanceStatusSelectableMappingService;

    public EventCategorySelectableMappingService(
            InstanceStatusSelectableMappingService instanceStatusSelectableMappingService
    ) {
        this.instanceStatusSelectableMappingService = instanceStatusSelectableMappingService;
    }

    public Selectable<String> toSelectable(EventCategory eventCategory) {
        return Selectable
                .<String>builder()
                .value(eventCategory.name())
                .label(getDisplayText(eventCategory))
                .build();
    }

    private String getDisplayText(EventCategory eventCategory) {
        return switch (eventCategory) {
            case INSTANCE_RECEIVED -> "Mottatt";
            case INSTANCE_REGISTERED -> "Mellomlagret";
            case INSTANCE_REQUESTED_FOR_RETRY -> "Forespurt for nytt forsøk";
            case INSTANCE_MAPPED -> "Konvertert";
            case INSTANCE_READY_FOR_DISPATCH -> "Klar for sending til destinasjon";
            case INSTANCE_DISPATCHED -> "Sendt til destinasjon";
            case INSTANCE_MANUALLY_PROCESSED -> "Manuelt behandlet";
            case INSTANCE_MANUALLY_REJECTED -> "Manuelt avvist";
            case INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED ->
                    "Status manuelt overstyrt som '" + instanceStatusSelectableMappingService.getDisplayText(InstanceStatus.TRANSFERRED) + "'";
            case INSTANCE_RECEIVAL_ERROR -> "Feilet under mottak";
            case INSTANCE_REGISTRATION_ERROR -> "Feilet under registrering";
            case INSTANCE_RETRY_REQUEST_ERROR -> "Feilet under forespørsel om nytt forsøk";
            case INSTANCE_MAPPING_ERROR -> "Feilet under konvertering";
            case INSTANCE_DISPATCHING_ERROR -> "Feilet under sending til destinasjon";
            case INSTANCE_DELETED -> "Mellomlagring av instans slettet";
        };
    }

}
