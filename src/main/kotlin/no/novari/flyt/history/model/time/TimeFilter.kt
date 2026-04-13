package no.novari.flyt.history.model.time

import jakarta.validation.Valid
import no.novari.flyt.history.model.instance.ActiveTimePeriod
import no.novari.flyt.history.validation.OnlyOneTimeFilterType

@OnlyOneTimeFilterType
data class TimeFilter(
    @field:Valid
    val offset: OffsetTimeFilter? = null,
    @field:Valid
    val currentPeriod: ActiveTimePeriod? = null,
    @field:Valid
    val manual: ManualTimeFilter? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var offset: OffsetTimeFilter? = null
        private var currentPeriod: ActiveTimePeriod? = null
        private var manual: ManualTimeFilter? = null

        fun offset(offset: OffsetTimeFilter?) = apply { this.offset = offset }

        fun currentPeriod(currentPeriod: ActiveTimePeriod?) = apply { this.currentPeriod = currentPeriod }

        fun manual(manual: ManualTimeFilter?) = apply { this.manual = manual }

        fun build() =
            TimeFilter(
                offset = offset,
                currentPeriod = currentPeriod,
                manual = manual,
            )
    }
}
