package no.novari.flyt.history.kafka;

import no.novari.kafka.topic.ErrorEventTopicService;
import no.novari.kafka.topic.configuration.EventCleanupFrequency;
import no.novari.kafka.topic.configuration.EventTopicConfiguration;
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static no.novari.flyt.history.model.event.EventCategory.INSTANCE_RECEIVAL_ERROR;

@Configuration
public class InstanceReceivalErrorConfiguration {

    public InstanceReceivalErrorConfiguration(
            ErrorEventTopicService errorEventTopicService,
            @Value("${novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time}") Duration retentionTime
    ) {
        ErrorEventTopicNameParameters instanceProcessingErrorTopicNameParameters = ErrorEventTopicNameParameters.builder()
                .errorEventName(INSTANCE_RECEIVAL_ERROR.getEventName())
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters
                                .builder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .build();

        errorEventTopicService.createOrModifyTopic(
                instanceProcessingErrorTopicNameParameters,
                EventTopicConfiguration
                        .builder().partitions(1)
                        .retentionTime(retentionTime)
                        .cleanupFrequency(EventCleanupFrequency.NORMAL)
                        .build()
        );
    }
}
