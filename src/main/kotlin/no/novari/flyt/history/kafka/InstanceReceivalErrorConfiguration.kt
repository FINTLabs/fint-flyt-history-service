package no.novari.flyt.history.kafka

import no.novari.flyt.history.model.event.EventCategory.INSTANCE_RECEIVAL_ERROR
import no.novari.kafka.topic.ErrorEventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class InstanceReceivalErrorConfiguration(
    errorEventTopicService: ErrorEventTopicService,
    @Value("\${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}")
    retentionTime: Duration,
) {
    init {
        errorEventTopicService.createOrModifyTopic(
            ErrorEventTopicNameParameters
                .builder()
                .errorEventName(INSTANCE_RECEIVAL_ERROR.eventName)
                .topicNamePrefixParameters(
                    TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build(),
                ).build(),
            EventTopicConfiguration
                .stepBuilder()
                .partitions(1)
                .retentionTime(retentionTime)
                .cleanupFrequency(EventCleanupFrequency.NORMAL)
                .build(),
        )
    }
}
