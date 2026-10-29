package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders

internal object InstanceInputValidator {
    fun validate(instance: InstanceObjectDto) {
        validateObject(instance, "$")
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

    private fun validateObject(
        instance: InstanceObjectDto,
        path: String,
    ) {
        instance.valuePerKey.entries.forEachIndexed { index, (key, value) ->
            validateKey(key, "$path.valuePerKey[$index].key")
            validateText(value, "$path.valuePerKey[$index].value")
        }
        instance.objectCollectionPerKey.entries.forEachIndexed { index, (key, children) ->
            val collectionPath = "$path.objectCollectionPerKey[$index]"
            validateKey(key, "$collectionPath.key")
            children.forEachIndexed { childIndex, child ->
                val childPath = "$collectionPath.objects[$childIndex]"
                validateObject(child, childPath)
            }
        }
    }

    private fun validateKey(
        key: String,
        path: String,
    ) {
        if (key.codePointCount(0, key.length) > 255) {
            throw InstanceInputException(InstanceInputErrorCode.KEY_TOO_LONG, path)
        }
        validateText(key, path)
    }

    private fun validateText(
        value: String,
        path: String,
    ) {
        if ('\u0000' in value) {
            throw InstanceInputException(InstanceInputErrorCode.TEXT_CONTAINS_NULL_CHARACTER, path)
        }
    }
}
