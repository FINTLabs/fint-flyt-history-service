package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.kafka.instanceflow.headers.INSTANCE_FLOW_HEADERS_KEY
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.kafka.starter.producer.OriginHeaderProducerInterceptor.Companion.ORIGIN_APPLICATION_ID_RECORD_HEADER
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import org.springframework.util.backoff.FixedBackOff
import java.time.Instant

@Configuration
class EventListenerConfiguration {
    /** One attempt, then log and skip, as `noRetries().skipFailedRecords()` did. instance-received has its own. */
    @Bean
    fun kafkaErrorHandler(): CommonErrorHandler = DefaultErrorHandler(FixedBackOff(0, 0))
}

/** History events, all in the application's group, as before. */
@Component
class HistoryEventListener(
    private val historyEventTopics: HistoryEventTopics,
    private val instanceInfoEventService: InstanceInfoEventService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val instanceDeletionService: InstanceDeletionService,
) {
    @KafkaListener(topics = ["#{@historyEventTopics.info}"])
    fun onInfoEvent(
        @Header(INSTANCE_FLOW_HEADERS_KEY) headers: InstanceFlowHeaders,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long,
        @Header(ORIGIN_APPLICATION_ID_RECORD_HEADER) origin: ByteArray,
        @Header(name = ActorHeader.HEADER_NAME, required = false) actor: ByteArray?,
    ) {
        val category = historyEventTopics.category(topic)
        instanceInfoEventService.registerEvent(
            instanceFlowHeaders = headers,
            eventCategory = category,
            timestamp = Instant.ofEpochMilli(timestamp),
            applicationId = origin.decodeToString(),
            actor =
                if (category ==
                    EventCategory.INSTANCE_REQUESTED_FOR_RETRY
                ) {
                    ActorHeader.fromHeaderValueOrNull(actor)
                } else {
                    null
                },
        )
        // Same group as the history event, so no separate offsets need to be carried over at cutover.
        if (category == EventCategory.INSTANCE_DISPATCHED) {
            instanceDeletionService.delete(headers)
        }
    }

    @KafkaListener(topics = ["#{@historyEventTopics.instanceError}"])
    fun onInstanceError(
        event: InstanceErrorEvent,
        @Header(INSTANCE_FLOW_HEADERS_KEY) headers: InstanceFlowHeaders,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long,
        @Header(ORIGIN_APPLICATION_ID_RECORD_HEADER) origin: ByteArray,
    ) {
        instanceErrorEventService.registerError(
            instanceFlowHeaders = headers,
            instanceErrorEvent = event,
            timestamp = Instant.ofEpochMilli(timestamp),
            applicationId = origin.decodeToString(),
        )
    }
}

/** The error topics before instance-error. Turned off with `legacy-error-topic-listeners-enabled: false`. */
@Component
@ConditionalOnProperty(
    prefix = "novari.flyt.history-service.kafka",
    name = ["legacy-error-topic-listeners-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class LegacyErrorEventListener(
    private val historyEventTopics: HistoryEventTopics,
    private val instanceErrorEventService: InstanceErrorEventService,
) {
    @KafkaListener(topics = ["#{@historyEventTopics.legacyErrors}"])
    fun onErrorEvent(
        errors: ErrorCollection,
        @Header(INSTANCE_FLOW_HEADERS_KEY) headers: InstanceFlowHeaders,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long,
        @Header(ORIGIN_APPLICATION_ID_RECORD_HEADER) origin: ByteArray,
    ) {
        instanceErrorEventService.registerError(
            instanceFlowHeaders = headers,
            eventCategory = historyEventTopics.category(topic),
            errorCollection = errors,
            timestamp = Instant.ofEpochMilli(timestamp),
            applicationId = origin.decodeToString(),
        )
    }
}
