package no.novari.flyt.history.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MinTimestampBeforeMaxTimestampValidator::class])
annotation class MinTimestampBeforeMaxTimestamp(
    val message: String = "timestampMin must be before timestampMax",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
