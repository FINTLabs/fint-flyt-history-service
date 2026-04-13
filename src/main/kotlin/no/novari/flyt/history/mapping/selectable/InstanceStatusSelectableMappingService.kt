package no.novari.flyt.history.mapping.selectable

import no.novari.flyt.history.model.Selectable
import no.novari.flyt.history.model.instance.InstanceStatus
import org.springframework.stereotype.Service

@Service
class InstanceStatusSelectableMappingService {
    fun toSelectable(instanceStatus: InstanceStatus): Selectable<String> {
        return Selectable(
            value = instanceStatus.name,
            label = getDisplayText(instanceStatus),
        )
    }

    fun getDisplayText(instanceStatus: InstanceStatus): String {
        return when (instanceStatus) {
            InstanceStatus.IN_PROGRESS -> "Under behandling"
            InstanceStatus.TRANSFERRED -> "Overført"
            InstanceStatus.ABORTED -> "Avbrutt"
            InstanceStatus.FAILED -> "Feilet"
        }
    }
}
