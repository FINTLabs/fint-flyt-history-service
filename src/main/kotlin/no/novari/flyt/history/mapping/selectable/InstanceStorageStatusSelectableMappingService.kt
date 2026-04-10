package no.novari.flyt.history.mapping.selectable

import no.novari.flyt.history.model.Selectable
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import org.springframework.stereotype.Service

@Service
class InstanceStorageStatusSelectableMappingService {
    fun toSelectable(instanceStorageStatus: InstanceStorageStatus): Selectable<String> {
        return Selectable(
            value = instanceStorageStatus.name,
            label = getDisplayText(instanceStorageStatus),
        )
    }

    private fun getDisplayText(instanceStorageStatus: InstanceStorageStatus): String {
        return when (instanceStorageStatus) {
            InstanceStorageStatus.STORED -> "Lagret"
            InstanceStorageStatus.STORED_AND_DELETED -> "Lagret og slettet"
            InstanceStorageStatus.NEVER_STORED -> "Aldri lagret"
        }
    }
}
