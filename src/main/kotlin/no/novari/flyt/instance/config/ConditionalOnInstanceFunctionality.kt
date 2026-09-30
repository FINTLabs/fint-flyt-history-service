package no.novari.flyt.instance.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ConditionalOnProperty(
    prefix = "novari.flyt.instance-functionality",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnInstanceFunctionality
