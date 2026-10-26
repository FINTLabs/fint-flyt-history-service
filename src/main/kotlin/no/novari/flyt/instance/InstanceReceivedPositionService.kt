package no.novari.flyt.instance

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceReceivedKafkaMetadataService
import no.novari.flyt.instance.kafka.InstanceReceivedTopicMetadata
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
@ConditionalOnInstanceFunctionality
@Transactional(propagation = Propagation.NEVER)
class InstanceReceivedPositionService(
    private val metadataService: InstanceReceivedKafkaMetadataService,
    private val offsetRepository: InstanceReceivedOffsetRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager)

    fun getValidatedPosition(partition: Int): InstanceReceivedPosition {
        val metadata = metadataService.read()
        if (partition !in metadata.partitions) {
            throw InstanceReceivedOffsetException(
                "Partition does not exist for topic=${metadata.topic}, partition=$partition",
            )
        }
        val position =
            offsetRepository.find(metadata.topic, partition)
                ?: throw InstanceReceivedOffsetException(
                    "No instance-received offset initialized for topic=${metadata.topic}, partition=$partition",
                )
        validate(position, metadata)
        return position
    }

    fun initialize(request: InstanceReceivedPositionInitialization): InstanceReceivedPosition {
        if (request.topic.isBlank() || request.clusterId.isBlank() || request.topicId == UUID(0, 0) ||
            request.partition < 0 || request.nextOffset < 0
        ) {
            throw InstanceReceivedOffsetException("Invalid instance-received position initialization")
        }
        val expected = request.expectedPosition
        if (expected is ExpectedInstanceReceivedPosition.Legacy && expected.nextOffset != request.nextOffset) {
            throw InstanceReceivedOffsetException(
                "Binding cluster and topic identity must preserve the expected legacy offset",
            )
        }
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
        )
        return writeTransaction.execute { offsetRepository.initialize(request) }!!
    }

    private fun validate(
        position: InstanceReceivedPosition,
        metadata: InstanceReceivedTopicMetadata,
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
        val bounds =
            metadata.partitions[position.partition]
                ?: throw InstanceReceivedOffsetException(
                    "Partition does not exist for topic=${position.topic}, partition=${position.partition}",
                )
        if (position.nextOffset < bounds.earliestOffset || position.nextOffset > bounds.endOffset) {
            throw InstanceReceivedOffsetException(
                "Instance-received offset ${position.nextOffset} is outside broker bounds " +
                    "[${bounds.earliestOffset}, ${bounds.endOffset}] for topic=${position.topic}, partition=${position.partition}",
            )
        }
    }
}
