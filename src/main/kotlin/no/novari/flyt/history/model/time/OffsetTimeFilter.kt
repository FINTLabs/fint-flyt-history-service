package no.novari.flyt.history.model.time

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.PositiveOrZero

data class OffsetTimeFilter(
    @field:JsonProperty
    @field:PositiveOrZero
    val hours: Int? = null,
    @field:JsonProperty
    @field:PositiveOrZero
    val minutes: Int? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var hours: Int? = null
        private var minutes: Int? = null

        fun hours(hours: Int?) = apply { this.hours = hours }

        fun minutes(minutes: Int?) = apply { this.minutes = minutes }

        fun build() = OffsetTimeFilter(hours = hours, minutes = minutes)
    }
}
