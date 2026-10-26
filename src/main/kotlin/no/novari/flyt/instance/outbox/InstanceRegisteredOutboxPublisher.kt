package no.novari.flyt.instance.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceRegisteredEventProducerService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegisteredOutboxPublisher(
    private val outboxService: InstanceRegisteredOutboxService,
    private val codec: InstanceRegisteredOutboxCodec,
    private val producer: InstanceRegisteredEventProducerService,
    private val properties: InstanceRegisteredOutboxProperties,
    meterRegistry: MeterRegistry,
) {
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(false)
    private val statistics = AtomicReference(InstanceRegisteredOutboxStatistics(0, 0.0))
    private val failures = meterRegistry.counter("flyt.instance.outbox.publish.failures")

    init {
        Gauge
            .builder(
                "flyt.instance.outbox.pending",
                statistics,
            ) { it.get().pending.toDouble() }
            .register(meterRegistry)
        Gauge
            .builder("flyt.instance.outbox.oldest.age.seconds", statistics) {
                it.get().oldestAgeSeconds
            }.register(meterRegistry)
    }

    @Scheduled(
        fixedDelayString = $$"${novari.flyt.instance.outbox.poll-delay:1s}",
        scheduler = "instanceRegisteredOutboxScheduler",
    )
    @Transactional(propagation = Propagation.NEVER)
    fun publishPending() {
        if (!running.compareAndSet(false, true)) return
        try {
            var processed = 0
            while (processed++ < properties.maxRecordsPerRun && !Thread.currentThread().isInterrupted) {
                val claim = outboxService.claimNext() ?: break
                try {
                    producer.publish(codec.decode(claim))
                    if (!outboxService.complete(claim)) {
                        logger.info {
                            "Outbox completion ignored for stale claim: id=${claim.id}, token=${claim.token}"
                        }
                    }
                } catch (failure: Exception) {
                    if (failure is InterruptedException) Thread.currentThread().interrupt()
                    failures.increment()
                    val errorType = failure.cause?.javaClass?.name ?: failure.javaClass.name
                    outboxService.fail(claim, errorType)
                    logger.warn {
                        "Outbox publication unresolved: id=${claim.id}, topic=${claim.messageId.topic}, " +
                            "partition=${claim.messageId.partition}, offset=${claim.messageId.offset}, " +
                            "attempt=${claim.attempts}, errorType=$errorType"
                    }
                    if (failure is InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        } catch (failure: Exception) {
            logger.error { "Outbox processing stopped: errorType=${failure.javaClass.name}" }
        } finally {
            try {
                statistics.set(outboxService.statistics())
            } catch (failure: Exception) {
                logger.error { "Outbox metrics refresh failed: errorType=${failure.javaClass.name}" }
            }
            running.set(false)
        }
    }
}
