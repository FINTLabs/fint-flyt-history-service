package no.novari.flyt.instance.kafka

import org.apache.kafka.common.KafkaFuture
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class MetadataLookupDeadline(
    timeout: Duration,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val expiresAt = nanoTime() + timeout.toNanos()

    fun remaining(): Duration = Duration.ofNanos((expiresAt - nanoTime()).coerceAtLeast(0))

    fun <T> await(future: KafkaFuture<T>): T {
        val remainingNanos = remaining().toNanos()
        if (remainingNanos == 0L) throw TimeoutException("Instance-received metadata lookup timed out")
        return future.get(remainingNanos, TimeUnit.NANOSECONDS)
    }
}
