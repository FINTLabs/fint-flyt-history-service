package no.novari.flyt.instance.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.InstanceReceivedOffsetException
import no.novari.flyt.instance.safeDiagnosticCause
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.NoOffsetForPartitionException
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.util.backoff.FixedBackOff

internal class InstanceReceivedProcessingException(
    context: String,
    failure: Exception,
    val retryable: Boolean,
) : RuntimeException("$context (${failure.javaClass.name})", safeDiagnosticCause(failure)) {
    init {
        stackTrace = failure.stackTrace
    }
}

internal class InstanceReceivedErrorHandler(
    private val targetContainer: MessageListenerContainer,
    properties: InstanceReceivedKafkaProperties,
    private val stopping: CommonContainerStoppingErrorHandler = CommonContainerStoppingErrorHandler(),
) : DefaultErrorHandler(
        ConsumerAwareRecordRecoverer { record, consumer, failure ->
            consumerLogger.error(failure) {
                "Stopping instance-received consumer: topic=${record.topic()}, partition=${record.partition()}, offset=${record.offset()}"
            }
            stopping.handleRemaining(failure, listOf(record), checkNotNull(consumer), targetContainer)
        },
        FixedBackOff(properties.retryInterval.toMillis(), properties.retryAttempts),
    ) {
    init {
        setBackOffFunction { _, failure ->
            val processingFailure =
                generateSequence(failure as Throwable) { it.cause }
                    .filterIsInstance<InstanceReceivedProcessingException>()
                    .firstOrNull()
            if (processingFailure?.retryable == true) {
                FixedBackOff(properties.retryInterval.toMillis(), properties.retryAttempts)
            } else {
                FixedBackOff(0, 0)
            }
        }
        setResetStateOnExceptionChange(false)
        setResetStateOnRecoveryFailure(false)
        isAckAfterHandle = false
    }

    override fun handleOtherException(
        thrownException: Exception,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
        batchListener: Boolean,
    ) {
        val failure =
            generateSequence(thrownException as Throwable) { it.cause }
                .filterIsInstance<Exception>()
                .firstOrNull {
                    it is InstanceReceivedOffsetException || it is InstanceReceivedMetadataException ||
                        it is OffsetOutOfRangeException || it is NoOffsetForPartitionException
                } ?: InstanceReceivedProcessingException(
                "Instance-received consumer failed outside record processing",
                thrownException,
                false,
            )
        consumerLogger.error(failure) { "Stopping instance-received consumer: listener=${container.listenerId}" }
        stopping.handleOtherException(failure, consumer, targetContainer, batchListener)
    }

    private companion object {
        val consumerLogger = KotlinLogging.logger {}
    }
}
