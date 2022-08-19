package no.fintlabs;

import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import no.fintlabs.repositories.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class ArchiveCaseFolderIdRequestConsumerConfiguration {

    private final EventRepository eventRepository;

    public ArchiveCaseFolderIdRequestConsumerConfiguration(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Bean
    ConcurrentMessageListenerContainer<String, String> archiveCaseFolderIdRequestConsumer(
            RequestTopicService requestTopicService,
            RequestConsumerFactoryService requestConsumerFactoryService
    ) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resource("archive.case.folder.id")
                .parameterName("source-application-instance-id")
                .build();

        requestTopicService.ensureTopic(topicNameParameters, 0, TopicCleanupPolicyParameters.builder().build());

        return requestConsumerFactoryService.createFactory(
                String.class,
                String.class,
                consumerRecord -> {
                    // TODO: 21/06/2022 Where do we get source application from?
                    String archiveCaseFolderId = eventRepository.findArchiveCaseFolderId("", consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<String>builder().value(archiveCaseFolderId).build();
                },
                null
        ).createContainer(topicNameParameters);
    }

}
