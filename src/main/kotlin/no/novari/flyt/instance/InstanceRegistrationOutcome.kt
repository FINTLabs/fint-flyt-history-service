package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto

sealed interface InstanceRegistrationOutcome {
    data class Registered(
        val instance: InstanceObjectDto,
    ) : InstanceRegistrationOutcome

    data class Rejected(
        val code: InstanceInputErrorCode,
    ) : InstanceRegistrationOutcome

    data object AlreadyProcessed : InstanceRegistrationOutcome
}
