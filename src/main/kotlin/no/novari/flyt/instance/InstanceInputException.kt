package no.novari.flyt.instance

enum class InstanceInputErrorCode {
    KEY_TOO_LONG,
    TEXT_CONTAINS_NULL_CHARACTER,
}

class InstanceInputException(
    val code: InstanceInputErrorCode,
) : RuntimeException("Invalid instance-received input: code=$code")
