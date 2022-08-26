package no.fintlabs.consumers;

import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import no.fintlabs.model.ArchiveCaseIdRequestParams;
import no.fintlabs.repositories.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class ArchiveCaseIdRequestConsumerConfiguration {

    private final EventRepository eventRepository;

    public ArchiveCaseIdRequestConsumerConfiguration(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ArchiveCaseIdRequestParams> archiveCaseIdRequestConsumer(
            RequestTopicService requestTopicService,
            RequestConsumerFactoryService requestConsumerFactoryService
    ) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resource("archive.case.id")
                .parameterName("source-application-instance-id")
                .build();

        requestTopicService.ensureTopic(topicNameParameters, 0, TopicCleanupPolicyParameters.builder().build());

        return requestConsumerFactoryService.createFactory(
                ArchiveCaseIdRequestParams.class,
                String.class,
                consumerRecord -> {
                    String archiveCaseId = eventRepository.findArchiveCaseId(
                                    consumerRecord.value().getSourceApplicationId(),
                                    consumerRecord.value().getSourceApplicationInstanceId()
                            )
                            .orElse(null);
                    return ReplyProducerRecord.<String>builder().value(archiveCaseId).build();
                },
                null
        ).createContainer(topicNameParameters);
    }

}
