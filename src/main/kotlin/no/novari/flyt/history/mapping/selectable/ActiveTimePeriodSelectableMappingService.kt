package no.novari.flyt.history.mapping.selectable

import no.novari.flyt.history.model.Selectable
import no.novari.flyt.history.model.instance.ActiveTimePeriod
import org.springframework.stereotype.Service

@Service
class ActiveTimePeriodSelectableMappingService {
    fun toSelectable(activeTimePeriod: ActiveTimePeriod): Selectable<String> {
        return Selectable(
            value = activeTimePeriod.name,
            label = getDisplayText(activeTimePeriod),
        )
    }

    private fun getDisplayText(activeTimePeriod: ActiveTimePeriod): String {
        return when (activeTimePeriod) {
            ActiveTimePeriod.TODAY -> "Denne dagen"
            ActiveTimePeriod.THIS_WEEK -> "Denne uka"
            ActiveTimePeriod.THIS_MONTH -> "Denne måneden"
            ActiveTimePeriod.THIS_YEAR -> "Dette året"
        }
    }
}
