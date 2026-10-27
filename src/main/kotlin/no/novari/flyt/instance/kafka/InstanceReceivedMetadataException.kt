package no.novari.flyt.instance.kafka

class InstanceReceivedMetadataException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
