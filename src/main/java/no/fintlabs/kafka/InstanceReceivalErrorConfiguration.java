package no.fintlabs.kafka;

import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters;
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceReceivalErrorConfiguration {

    public InstanceReceivalErrorConfiguration(
            ErrorEventTopicService errorEventTopicService,
            @Value("${fint.kafka.topic.instance-retention-ms}") long retentionMs
    ) {
        ErrorEventTopicNameParameters instanceProcessingErrorTopicNameParameters = ErrorEventTopicNameParameters.builder()
                .errorEventName("instance-receival-error")
                .build();

        errorEventTopicService.ensureTopic(instanceProcessingErrorTopicNameParameters, retentionMs);
    }
}
