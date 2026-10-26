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
)

sealed interface ExpectedInstanceReceivedPosition {
    data object Missing : ExpectedInstanceReceivedPosition

    data class Legacy(
        val nextOffset: Long,
    ) : ExpectedInstanceReceivedPosition
}
