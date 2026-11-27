package no.novari.flyt.history.kafka

import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.kafka.starter.topic.EventCleanupFrequency
import no.novari.kafka.starter.topic.EventTopicConfig
import no.novari.kafka.starter.topic.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration

/** The topics history listens to, and the event category of each. */
@Component("historyEventTopics")
class HistoryEventTopics(
    topics: KafkaTopics,
) {
    private val categoryByTopic: Map<String, EventCategory> =
        EventCategory.entries.filter(EventCategory::createKafkaListener).associateBy {
            when (it.type) {
                EventType.INFO -> topics.event(it.eventName).name
                EventType.ERROR -> topics.errorEvent(it.eventName).name
            }
        }

    val info: Array<String> = topicsOf(EventType.INFO)
    val legacyErrors: Array<String> = topicsOf(EventType.ERROR)
    val instanceError: String = topics.errorEvent(INSTANCE_ERROR).name

    fun category(topic: String): EventCategory = categoryByTopic.getValue(topic)

    private fun topicsOf(type: EventType) = categoryByTopic.filterValues { it.type == type }.keys.toTypedArray()

    companion object {
        const val INSTANCE_ERROR = "instance-error"
    }
}

@Configuration
class HistoryTopicConfiguration(
    private val topics: KafkaTopics,
    @param:Value($$"${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    private val retentionTime: Duration,
) {
    @Bean
    fun instanceErrorTopicCreation(): NewTopic = errorEventTopic(HistoryEventTopics.INSTANCE_ERROR)

    @Bean
    @ConditionalOnProperty(
        prefix = "novari.flyt.history-service.kafka",
        name = ["legacy-error-topic-listeners-enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun instanceReceivalErrorTopicCreation(): NewTopic =
        errorEventTopic(EventCategory.INSTANCE_RECEIVAL_ERROR.eventName)

    private fun errorEventTopic(name: String): NewTopic =
        topics.newTopic(topics.errorEvent(name), eventTopicConfig(retentionTime))
}

/** Config of the instance processing event topics history creates: 1 partition, cleanup NORMAL. */
internal fun eventTopicConfig(retentionTime: Duration) =
    EventTopicConfig(retention = retentionTime, cleanupFrequency = EventCleanupFrequency.NORMAL, partitions = 1)
