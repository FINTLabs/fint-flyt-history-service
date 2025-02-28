package no.fintlabs.repository.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.model.event.EventCategory;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public class SequenceGenerationConfig {
    private final List<EventCategory> eventSequence;
    private final int numberOfSequences;
    private final String sourceApplicationInstanceIdOverride;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<EventCategory> eventSequence;
        private int numberOfSequences;
        private String sourceApplicationInstanceIdOverride;

        private Builder() {
        }

        public Builder eventSequence(EventSequence eventSequence) {
            return eventSequence(eventSequence.getOrder());
        }

        public Builder eventSequence(EventCategory... eventSequence) {
            return eventSequence(Arrays.stream(eventSequence).toList());
        }

        public Builder eventSequence(List<EventCategory> eventSequence) {
            this.eventSequence = eventSequence;
            return this;
        }

        public Builder numberOfSequences(int numberOfSequences) {
            this.numberOfSequences = numberOfSequences;
            return this;
        }

        public Builder sourceApplicationInstanceIdOverride(String sourceApplicationInstanceIdOverride) {
            this.sourceApplicationInstanceIdOverride = sourceApplicationInstanceIdOverride;
            return this;
        }

        public SequenceGenerationConfig build() {
            return new SequenceGenerationConfig(
                    eventSequence,
                    numberOfSequences,
                    sourceApplicationInstanceIdOverride
            );
        }
    }
}
