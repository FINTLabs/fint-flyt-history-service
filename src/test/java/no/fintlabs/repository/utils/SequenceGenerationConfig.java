package no.fintlabs.repository.utils;

import lombok.Getter;
import no.fintlabs.model.event.EventCategory;

import java.util.List;

@Getter
public class SequenceGenerationConfig {
    private final List<EventCategory> eventSequence;
    private final int numberOfSequences;
    private final String sourceApplicationInstanceIdOverride;

    public SequenceGenerationConfig(
            List<EventCategory> eventSequence,
            int numberOfSequences,
            String sourceApplicationInstanceIdOverride
    ) {
        this.eventSequence = eventSequence;
        this.numberOfSequences = numberOfSequences;
        this.sourceApplicationInstanceIdOverride = sourceApplicationInstanceIdOverride;
    }

    public SequenceGenerationConfig(
            EventSequence eventSequence,
            int numberOfSequences,
            String sourceApplicationInstanceIdOverride
    ) {
        this(eventSequence.getOrder(), numberOfSequences, sourceApplicationInstanceIdOverride);
    }

    public SequenceGenerationConfig(
            List<EventCategory> eventSequence,
            int numberOfSequences
    ) {
        this(eventSequence, numberOfSequences, null);
    }

    public SequenceGenerationConfig(
            EventSequence eventSequence,
            int numberOfSequences
    ) {
        this(eventSequence.getOrder(), numberOfSequences);
    }

}
