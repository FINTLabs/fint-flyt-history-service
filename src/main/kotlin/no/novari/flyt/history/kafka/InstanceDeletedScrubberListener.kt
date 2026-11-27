package no.novari.flyt.history.kafka

import no.novari.flyt.kafka.instanceflow.headers.INSTANCE_FLOW_HEADERS_KEY
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.AbstractConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

/** Own group, read from the beginning. The suffix has no separator, as in fint-kafka 6. */
@Component
class InstanceDeletedScrubberListener : AbstractConsumerSeekAware() {
    @KafkaListener(
        topics = ["#{@instanceDeletedTopic.name}"],
        groupId = "#{@kafkaGroupIds.withSuffix('instance-deleted-scrubber')}",
    )
    @Suppress("UNUSED_PARAMETER")
    fun on(
        @Header(INSTANCE_FLOW_HEADERS_KEY) headers: InstanceFlowHeaders,
    ) {
        // Midlertidig deaktivert: umiddelbar scrubbing ved instance-deleted er skrudd av.
        // Scrubbing skjer nå kun via ScheduledErrorScrubService (etter retention-perioden).
    }

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        super.onPartitionsAssigned(assignments, callback)
        callback.seekToBeginning(assignments.keys)
    }
}
