package no.novari.flyt.instance.model.dtos

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

data class InstanceObjectDto(
    @field:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val id: Long? = null,
    val valuePerKey: MutableMap<String, String> = mutableMapOf(),
    val objectCollectionPerKey: MutableMap<String, Collection<InstanceObjectDto>> = mutableMapOf(),
    @field:JsonSerialize(using = Jackson2DateSerializer::class)
    val createdAt: Date? = null,
)

/**
 * Writes dates as Jackson 2 did (`2026-01-15T10:20:30.000+00:00`), which the instance-registered contract fixes.
 * Jackson 3 would write `Z`. Reading is unchanged and accepts both.
 */
class Jackson2DateSerializer : ValueSerializer<Date>() {
    override fun serialize(
        value: Date,
        generator: JsonGenerator,
        context: SerializationContext,
    ) {
        generator.writeString(FORMAT.format(value.toInstant()))
    }

    private companion object {
        val FORMAT: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSxxx",
                ).withZone(ZoneOffset.UTC)
    }
}
