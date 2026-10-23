package no.novari.flyt.instance

import java.util.UUID

data class InstanceReceivedMessageId(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val clusterId: String,
    val topicId: UUID,
) {
    init {
        require(topic.isNotBlank()) { "Topic must not be blank" }
        require(partition >= 0) { "Partition must not be negative" }
        require(offset >= 0) { "Offset must not be negative" }
        require(offset < Long.MAX_VALUE) { "Offset must allow calculating the next offset" }
        require(clusterId.isNotBlank()) { "Cluster ID must not be blank" }
        require(topicId != UUID(0L, 0L)) { "Topic ID must not be zero" }
    }
}
