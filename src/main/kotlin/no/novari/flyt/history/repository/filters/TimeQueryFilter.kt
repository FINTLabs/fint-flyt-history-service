package no.novari.flyt.history.repository.filters

import java.time.OffsetDateTime

data class TimeQueryFilter(
    val latestStatusTimestampMin: OffsetDateTime? = null,
    val latestStatusTimestampMax: OffsetDateTime? = null,
) {
    companion object {
        @JvmField
        val EMPTY = TimeQueryFilter()

        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var latestStatusTimestampMinValue: OffsetDateTime? = null
        private var latestStatusTimestampMaxValue: OffsetDateTime? = null

        fun latestStatusTimestampMin(latestStatusTimestampMin: OffsetDateTime?) =
            apply {
                this.latestStatusTimestampMinValue = latestStatusTimestampMin
            }

        fun latestStatusTimestampMax(latestStatusTimestampMax: OffsetDateTime?) =
            apply {
                this.latestStatusTimestampMaxValue = latestStatusTimestampMax
            }

        fun build() =
            TimeQueryFilter(
                latestStatusTimestampMin = latestStatusTimestampMinValue,
                latestStatusTimestampMax = latestStatusTimestampMaxValue,
            )
    }
}
