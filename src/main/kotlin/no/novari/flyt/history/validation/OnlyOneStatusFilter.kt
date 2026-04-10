package no.novari.flyt.history.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [OnlyOneStatusFilterValidator::class])
annotation class OnlyOneStatusFilter(
    val message: String = "contains both latest status events and status",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
