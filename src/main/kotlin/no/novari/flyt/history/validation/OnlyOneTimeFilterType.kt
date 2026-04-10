package no.novari.flyt.history.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [OnlyOneTimeFilterTypeValidator::class])
annotation class OnlyOneTimeFilterType(
    val message: String = "contains multiple time filters",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
