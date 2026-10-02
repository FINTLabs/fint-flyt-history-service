package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowConsumerRecord
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.apache.kafka.common.header.Headers
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

@Configuration
class EventListenerConfiguration(
    private val instanceErrorEventService: InstanceErrorEventService,
    private val instanceInfoEventService: InstanceInfoEventService,
    private val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService,
    private val errorHandlerFactory: ErrorHandlerFactory,
    private val beanFactory: ConfigurableListableBeanFactory,
    @Value($$"${novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled:true}")
    private val legacyErrorTopicListenersEnabled: Boolean,
) {
    @Bean
    fun eventListenerContainers(): Map<String, ConcurrentMessageListenerContainer<String, *>> {
        return EventCategory.entries
            .filter(EventCategory::createKafkaListener)
            .filter(::isListenerEnabled)
            .associateBy(EventCategory::eventName, ::registerListenerBean)
    }

    private fun isListenerEnabled(category: EventCategory): Boolean {
        return legacyErrorTopicListenersEnabled || category !in LEGACY_ERROR_TOPIC_CATEGORIES
    }

    private fun registerListenerBean(category: EventCategory): ConcurrentMessageListenerContainer<String, *> {
        val container = createEventListener(category)
        beanFactory.registerSingleton("eventListener-${category.eventName}", container)
        return container
    }

    private fun createEventListener(eventCategory: EventCategory): ConcurrentMessageListenerContainer<String, *> {
        return when (eventCategory.type) {
            EventType.INFO -> createInfoEventListener(eventCategory)
            EventType.ERROR -> createErrorEventListener(eventCategory)
        }
    }

    private fun createInfoEventListener(category: EventCategory): ConcurrentMessageListenerContainer<String, Any> {
        return instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                Any::class.java,
                { instanceFlowConsumerRecord ->
                    instanceInfoEventService.registerEvent(
                        instanceFlowHeaders = instanceFlowConsumerRecord.instanceFlowHeaders,
                        eventCategory = category,
                        timestamp = Instant.ofEpochMilli(instanceFlowConsumerRecord.consumerRecord.timestamp()),
                        applicationId = getApplicationId(instanceFlowConsumerRecord.consumerRecord.headers()),
                        actor = retryActor(instanceFlowConsumerRecord, category),
                    )
                },
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .continueFromPreviousOffsetOnAssignment()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<Any>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(
                EventTopicNameParameters
                    .builder()
                    .eventName(category.eventName)
                    .topicNamePrefixParameters(topicNamePrefixParameters())
                    .build(),
            )
    }

    private fun retryActor(
        instanceFlowConsumerRecord: InstanceFlowConsumerRecord<Any>,
        category: EventCategory,
    ): Actor? {
        if (category != EventCategory.INSTANCE_REQUESTED_FOR_RETRY) {
            return null
        }

        return instanceFlowConsumerRecord
            .consumerRecord
            .headers()
            .lastHeader(ActorHeader.HEADER_NAME)
            ?.value()
            ?.let(ActorHeader::fromHeaderValueOrNull)
    }

    private fun createErrorEventListener(
        eventCategory: EventCategory,
    ): ConcurrentMessageListenerContainer<String, ErrorCollection> {
        return instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                ErrorCollection::class.java,
                { instanceFlowConsumerRecord ->
                    instanceErrorEventService.registerError(
                        instanceFlowHeaders = instanceFlowConsumerRecord.instanceFlowHeaders,
                        eventCategory = eventCategory,
                        errorCollection = instanceFlowConsumerRecord.consumerRecord.value(),
                        timestamp = Instant.ofEpochMilli(instanceFlowConsumerRecord.consumerRecord.timestamp()),
                        applicationId = getApplicationId(instanceFlowConsumerRecord.consumerRecord.headers()),
                    )
                },
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .continueFromPreviousOffsetOnAssignment()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<ErrorCollection>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(createErrorEventTopicNameParameters(eventCategory.eventName))
    }

    @Bean
    fun instanceErrorListener(): ConcurrentMessageListenerContainer<String, InstanceErrorEvent> {
        return instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                InstanceErrorEvent::class.java,
                { instanceFlowConsumerRecord ->
                    instanceErrorEventService.registerError(
                        instanceFlowHeaders = instanceFlowConsumerRecord.instanceFlowHeaders,
                        instanceErrorEvent = instanceFlowConsumerRecord.consumerRecord.value(),
                        timestamp = Instant.ofEpochMilli(instanceFlowConsumerRecord.consumerRecord.timestamp()),
                        applicationId = getApplicationId(instanceFlowConsumerRecord.consumerRecord.headers()),
                    )
                },
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .continueFromPreviousOffsetOnAssignment()
                    .build(),
                errorHandlerFactory.createErrorHandler(
                    ErrorHandlerConfiguration
                        .stepBuilder<InstanceErrorEvent>()
                        .noRetries()
                        .skipFailedRecords()
                        .build(),
                ),
            ).createContainer(createErrorEventTopicNameParameters("instance-error"))
    }

    private fun createErrorEventTopicNameParameters(errorEventName: String): ErrorEventTopicNameParameters {
        return ErrorEventTopicNameParameters
            .builder()
            .errorEventName(errorEventName)
            .topicNamePrefixParameters(topicNamePrefixParameters())
            .build()
    }

    private fun topicNamePrefixParameters(): TopicNamePrefixParameters {
        return TopicNamePrefixParameters
            .stepBuilder()
            .orgIdApplicationDefault()
            .domainContextApplicationDefault()
            .build()
    }

    private fun getApplicationId(headers: Headers): String {
        return String(
            requireNotNull(
                headers.lastHeader(OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER),
            ).value(),
            UTF_8,
        )
    }

    private companion object {
        val LEGACY_ERROR_TOPIC_CATEGORIES =
            setOf(
                EventCategory.INSTANCE_RECEIVAL_ERROR,
                EventCategory.INSTANCE_REGISTRATION_ERROR,
                EventCategory.INSTANCE_RETRY_REQUEST_ERROR,
                EventCategory.INSTANCE_MAPPING_ERROR,
                EventCategory.INSTANCE_DISPATCHING_ERROR,
            )
    }
}
