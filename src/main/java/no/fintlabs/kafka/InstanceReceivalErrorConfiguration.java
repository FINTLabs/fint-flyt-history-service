package no.fintlabs.kafka;

import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters;
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import static no.fintlabs.model.event.EventCategory.INSTANCE_RECEIVAL_ERROR;

@Configuration
public class InstanceReceivalErrorConfiguration {

    public InstanceReceivalErrorConfiguration(
            ErrorEventTopicService errorEventTopicService,
            @Value("${fint.flyt.history-service.kafka.topic.instance-processing-events-retention-time-ms}") long retentionMs
    ) {
        ErrorEventTopicNameParameters instanceProcessingErrorTopicNameParameters = ErrorEventTopicNameParameters.builder()
                .errorEventName(INSTANCE_RECEIVAL_ERROR.getEventName())
                .build();

        errorEventTopicService.ensureTopic(instanceProcessingErrorTopicNameParameters, retentionMs);
    }
}
