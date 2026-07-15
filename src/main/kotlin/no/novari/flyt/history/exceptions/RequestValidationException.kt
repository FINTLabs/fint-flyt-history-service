package no.novari.flyt.history.exceptions

class RequestValidationException(
    val formattedMessage: String,
) : RuntimeException(formattedMessage)
