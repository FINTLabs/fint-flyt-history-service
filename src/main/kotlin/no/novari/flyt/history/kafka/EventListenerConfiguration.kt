package no.novari.flyt.history.kafka

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.consuming.InstanceFlowListenerFactoryService
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.consuming.ErrorHandlerConfiguration
import no.novari.kafka.consuming.ErrorHandlerFactory
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.apache.kafka.common.header.Headers
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.ZoneOffset

@Configuration
class EventListenerConfiguration(
    private val eventRepository: EventRepository,
    private val instanceFlowListenerFactoryService: InstanceFlowListenerFactoryService,
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
    private val errorHandlerFactory: ErrorHandlerFactory,
    private val beanFactory: ConfigurableListableBeanFactory,
) {
    @Bean
    fun eventListenerContainers(): Map<String, ConcurrentMessageListenerContainer<String, *>> {
        return EventCategory.entries
            .filter(EventCategory::createKafkaListener)
            .associateBy(EventCategory::eventName, ::registerListenerBean)
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
                    eventRepository.save(
                        EventEntity(
                            instanceFlowHeaders =
                                instanceFlowHeadersMappingService.toEmbeddable(
                                    instanceFlowConsumerRecord.instanceFlowHeaders,
                                ),
                            name = category.eventName,
                            type = EventType.INFO,
                            timestamp =
                                Instant
                                    .ofEpochMilli(instanceFlowConsumerRecord.consumerRecord.timestamp())
                                    .atOffset(ZoneOffset.UTC),
                            applicationId = getApplicationId(instanceFlowConsumerRecord.consumerRecord.headers()),
                        ),
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

    private fun createErrorEventListener(
        eventCategory: EventCategory,
    ): ConcurrentMessageListenerContainer<String, ErrorCollection> {
        return instanceFlowListenerFactoryService
            .createRecordListenerContainerFactory(
                ErrorCollection::class.java,
                { instanceFlowConsumerRecord ->
                    eventRepository.save(
                        EventEntity(
                            instanceFlowHeaders =
                                instanceFlowHeadersMappingService.toEmbeddable(
                                    instanceFlowConsumerRecord.instanceFlowHeaders,
                                ),
                            name = eventCategory.eventName,
                            type = EventType.ERROR,
                            timestamp =
                                Instant
                                    .ofEpochMilli(instanceFlowConsumerRecord.consumerRecord.timestamp())
                                    .atOffset(ZoneOffset.UTC),
                            errors = mapToErrorEntities(instanceFlowConsumerRecord.consumerRecord.value()),
                            applicationId = getApplicationId(instanceFlowConsumerRecord.consumerRecord.headers()),
                        ),
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

    private fun mapToErrorEntities(errorCollection: ErrorCollection): MutableCollection<ErrorEntity> {
        return errorCollection.errors
            .map(::mapToErrorEntity)
            .toMutableList()
    }

    private fun mapToErrorEntity(errorFromEvent: Error): ErrorEntity {
        return ErrorEntity(
            errorCode = errorFromEvent.errorCode,
            args = errorFromEvent.args,
        )
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
}
