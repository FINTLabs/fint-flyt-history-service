package no.novari.flyt.instance

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceReceivedKafkaMetadataService
import no.novari.flyt.instance.kafka.InstanceReceivedPartitionOffsets
import no.novari.flyt.instance.kafka.InstanceReceivedTopicMetadata
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
@ConditionalOnInstanceFunctionality
@Transactional(propagation = Propagation.NEVER)
class InstanceReceivedPositionService(
    private val metadataService: InstanceReceivedKafkaMetadataService,
    private val offsetRepository: InstanceReceivedOffsetRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager)

    fun getValidatedPosition(partition: Int): InstanceReceivedPosition =
        getValidatedPositions(setOf(partition)).getValue(partition)

    fun getValidatedPositions(partitions: Set<Int>): Map<Int, InstanceReceivedPosition> {
        if (partitions.isEmpty()) return emptyMap()
        val metadata = metadataService.read()
        return partitions.associateWith { partition ->
            val bounds = partitionBounds(metadata, partition)
            val position =
                offsetRepository.find(metadata.topic, partition)
                    ?: throw InstanceReceivedOffsetException(
                        "No instance-received offset initialized for topic=${metadata.topic}, partition=$partition",
                    )
            validate(position, metadata, bounds)
            position
        }
    }

    fun initialize(request: InstanceReceivedPositionInitialization): InstanceReceivedPosition {
        val metadata = metadataService.read()
        validate(
            InstanceReceivedPosition(
                request.topic,
                request.partition,
                request.nextOffset,
                request.clusterId,
                request.topicId,
            ),
            metadata,
            partitionBounds(metadata, request.partition),
        )
        return writeTransaction.execute { offsetRepository.initialize(request) }!!
    }

    private fun validate(
        position: InstanceReceivedPosition,
        metadata: InstanceReceivedTopicMetadata,
        bounds: InstanceReceivedPartitionOffsets,
    ) {
        if (position.clusterId == null || position.topicId == null) {
            throw InstanceReceivedOffsetException(
                "Cluster and topic identity is not initialized for topic=${position.topic}, partition=${position.partition}",
            )
        }
        if (position.topic != metadata.topic || position.clusterId != metadata.clusterId ||
            position.topicId != metadata.topicId
        ) {
            throw InstanceReceivedOffsetException(
                "Cluster or topic identity mismatch for topic=${position.topic}, partition=${position.partition}",
            )
        }
        if (position.nextOffset < bounds.earliestOffset || position.nextOffset > bounds.endOffset) {
            throw InstanceReceivedOffsetException(
                "Instance-received offset ${position.nextOffset} is outside broker bounds " +
                    "[${bounds.earliestOffset}, ${bounds.endOffset}] for topic=${position.topic}, partition=${position.partition}",
            )
        }
    }

    private fun partitionBounds(
        metadata: InstanceReceivedTopicMetadata,
        partition: Int,
    ): InstanceReceivedPartitionOffsets =
        metadata.partitions[partition]
            ?: throw InstanceReceivedOffsetException(
                "Partition does not exist for topic=${metadata.topic}, partition=$partition",
            )
}
