package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders

internal object InstanceInputValidator {
    fun validate(instance: InstanceObjectDto) {
        instance.valuePerKey.forEach { (key, value) ->
            validateKey(key)
            validateText(value)
        }
        instance.objectCollectionPerKey.forEach { (key, children) ->
            validateKey(key)
            children.forEach(::validate)
        }
    }

    fun validateHeaders(headers: InstanceFlowHeaders) {
        require(
            validHeaderText(headers.sourceApplicationIntegrationId, required = true) &&
                validHeaderText(headers.sourceApplicationInstanceId, required = true) &&
                validHeaderText(headers.archiveInstanceId, required = false),
        ) { "Invalid instance flow headers" }
    }

    private fun validHeaderText(
        value: String?,
        required: Boolean,
    ): Boolean = if (value == null) !required else value.codePointCount(0, value.length) <= 255 && '\u0000' !in value

    private fun validateKey(key: String) {
        if (key.codePointCount(0, key.length) > 255) {
            throw InstanceInputException(InstanceInputErrorCode.KEY_TOO_LONG)
        }
        validateText(key)
    }

    private fun validateText(value: String) {
        if ('\u0000' in value) {
            throw InstanceInputException(InstanceInputErrorCode.TEXT_CONTAINS_NULL_CHARACTER)
        }
    }
}
