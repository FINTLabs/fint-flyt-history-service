package no.novari.flyt.instance

class InstanceReceivedOffsetException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
