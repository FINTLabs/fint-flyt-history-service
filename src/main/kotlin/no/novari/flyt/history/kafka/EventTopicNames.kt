package no.novari.flyt.history.kafka

import no.novari.flyt.history.model.event.EventCategory
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters

internal fun eventTopicNameParameters(category: EventCategory): EventTopicNameParameters =
    EventTopicNameParameters
        .builder()
        .eventName(category.eventName)
        .topicNamePrefixParameters(
            TopicNamePrefixParameters
                .stepBuilder()
                .orgIdApplicationDefault()
                .domainContextApplicationDefault()
                .build(),
        ).build()
