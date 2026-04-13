package no.novari.flyt.history.mapping.selectable

import no.novari.flyt.history.model.Selectable
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.InstanceStatus
import org.springframework.stereotype.Service

@Service
class EventCategorySelectableMappingService(
    private val instanceStatusSelectableMappingService: InstanceStatusSelectableMappingService,
) {
    fun toSelectable(eventCategory: EventCategory): Selectable<String> {
        return Selectable(
            value = eventCategory.name,
            label = getDisplayText(eventCategory),
        )
    }

    private fun getDisplayText(eventCategory: EventCategory): String {
        return when (eventCategory) {
            EventCategory.INSTANCE_RECEIVED -> {
                "Mottatt"
            }

            EventCategory.INSTANCE_REGISTERED -> {
                "Mellomlagret"
            }

            EventCategory.INSTANCE_REQUESTED_FOR_RETRY -> {
                "Forespurt for nytt forsøk"
            }

            EventCategory.INSTANCE_MAPPED -> {
                "Konvertert"
            }

            EventCategory.INSTANCE_READY_FOR_DISPATCH -> {
                "Klar for sending til destinasjon"
            }

            EventCategory.INSTANCE_DISPATCHED -> {
                "Sendt til destinasjon"
            }

            EventCategory.INSTANCE_MANUALLY_PROCESSED -> {
                "Manuelt behandlet"
            }

            EventCategory.INSTANCE_MANUALLY_REJECTED -> {
                "Manuelt avvist"
            }

            EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED -> {
                "Status manuelt overstyrt som '${instanceStatusSelectableMappingService.getDisplayText(
                    InstanceStatus.TRANSFERRED,
                )}'"
            }

            EventCategory.INSTANCE_RECEIVAL_ERROR -> {
                "Feilet under mottak"
            }

            EventCategory.INSTANCE_REGISTRATION_ERROR -> {
                "Feilet under registrering"
            }

            EventCategory.INSTANCE_RETRY_REQUEST_ERROR -> {
                "Feilet under forespørsel om nytt forsøk"
            }

            EventCategory.INSTANCE_MAPPING_ERROR -> {
                "Feilet under konvertering"
            }

            EventCategory.INSTANCE_DISPATCHING_ERROR -> {
                "Feilet under sending til destinasjon"
            }

            EventCategory.INSTANCE_DELETED -> {
                "Mellomlagring av instans slettet"
            }
        }
    }
}
