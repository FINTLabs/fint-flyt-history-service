package no.novari.flyt.history.repository.utils.performance;

import lombok.Getter;
import lombok.ToString;
import no.novari.flyt.history.model.event.EventCategory;

import java.util.List;

@ToString
@Getter
public class EventSequenceGenerationConfig {
    private final List<EventCategory> eventSequence;
    private final int numberOfSequences;
    private final String sourceApplicationInstanceIdOverride;

    public EventSequenceGenerationConfig(
            List<EventCategory> eventSequence,
            int numberOfSequences,
            String sourceApplicationInstanceIdOverride
    ) {
        this.eventSequence = eventSequence;
        this.numberOfSequences = numberOfSequences;
        this.sourceApplicationInstanceIdOverride = sourceApplicationInstanceIdOverride;
    }

    public EventSequenceGenerationConfig(
            EventSequence eventSequence,
            int numberOfSequences,
            String sourceApplicationInstanceIdOverride
    ) {
        this(eventSequence.getOrder(), numberOfSequences, sourceApplicationInstanceIdOverride);
    }

    public EventSequenceGenerationConfig(
            List<EventCategory> eventSequence,
            int numberOfSequences
    ) {
        this(eventSequence, numberOfSequences, null);
    }

    public EventSequenceGenerationConfig(
            EventSequence eventSequence,
            int numberOfSequences
    ) {
        this(eventSequence.getOrder(), numberOfSequences);
    }

}
