package no.novari.flyt.history.model.time

import no.novari.flyt.history.validation.MinTimestampBeforeMaxTimestamp
import org.springframework.format.annotation.DateTimeFormat
import java.time.OffsetDateTime

@MinTimestampBeforeMaxTimestamp
data class ManualTimeFilter(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val min: OffsetDateTime? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val max: OffsetDateTime? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var min: OffsetDateTime? = null
        private var max: OffsetDateTime? = null

        fun min(min: OffsetDateTime?) = apply { this.min = min }

        fun max(max: OffsetDateTime?) = apply { this.max = max }

        fun build() = ManualTimeFilter(min = min, max = max)
    }
}
