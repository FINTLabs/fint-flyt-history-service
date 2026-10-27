package no.novari.flyt.instance

import java.util.UUID

data class InstanceReceivedPosition(
    val topic: String,
    val partition: Int,
    val nextOffset: Long,
    val clusterId: String?,
    val topicId: UUID?,
)

data class InstanceReceivedPositionInitialization(
    val topic: String,
    val partition: Int,
    val clusterId: String,
    val topicId: UUID,
    val nextOffset: Long,
    val expectedPosition: ExpectedInstanceReceivedPosition,
) {
    init {
        if (topic.isBlank()) throw InstanceReceivedOffsetException("Initialization topic must not be blank")
        if (partition < 0) throw InstanceReceivedOffsetException("Initialization partition must not be negative")
        if (clusterId.isBlank()) throw InstanceReceivedOffsetException("Initialization cluster ID must not be blank")
        if (topicId == UUID(0, 0)) throw InstanceReceivedOffsetException("Initialization topic ID must not be zero")
        if (nextOffset < 0) throw InstanceReceivedOffsetException("Initialization offset must not be negative")
    }
}

sealed interface ExpectedInstanceReceivedPosition {
    data object Missing : ExpectedInstanceReceivedPosition

    data object Legacy : ExpectedInstanceReceivedPosition
}
