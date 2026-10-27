package no.novari.flyt.instance.kafka

import java.util.UUID

data class InstanceReceivedTopicMetadata(
    val topic: String,
    val clusterId: String,
    val topicId: UUID,
    val partitions: Map<Int, InstanceReceivedPartitionOffsets>,
)

data class InstanceReceivedPartitionOffsets(
    val earliestOffset: Long,
    val endOffset: Long,
)
