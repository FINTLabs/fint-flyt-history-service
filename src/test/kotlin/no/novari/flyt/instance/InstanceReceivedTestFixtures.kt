package no.novari.flyt.instance

import no.novari.flyt.instance.kafka.InstanceReceivedPartitionOffsets
import no.novari.flyt.instance.kafka.InstanceReceivedTopicMetadata
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.UUID

const val RECEIVED_TOPIC = "test-org-flyt-instance-received"
const val RECEIVED_CLUSTER = "test-cluster"
val RECEIVED_TOPIC_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

fun receivedMetadata(
    earliestOffset: Long = 0,
    endOffset: Long = 20,
): InstanceReceivedTopicMetadata =
    InstanceReceivedTopicMetadata(
        RECEIVED_TOPIC,
        RECEIVED_CLUSTER,
        RECEIVED_TOPIC_ID,
        mapOf(0 to InstanceReceivedPartitionOffsets(earliestOffset, endOffset)),
    )

fun positionInitialization(nextOffset: Long = 10): InstanceReceivedPositionInitialization =
    InstanceReceivedPositionInitialization(
        RECEIVED_TOPIC,
        0,
        RECEIVED_CLUSTER,
        RECEIVED_TOPIC_ID,
        nextOffset,
        ExpectedInstanceReceivedPosition.Missing,
    )

fun receivedConsumerConfig(isolationLevel: String = "read_uncommitted"): ConsumerConfig =
    ConsumerConfig(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to isolationLevel,
        ),
    )
