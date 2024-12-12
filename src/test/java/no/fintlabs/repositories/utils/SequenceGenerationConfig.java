package no.fintlabs.repositories.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.model.eventinfo.EventInfo;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public class SequenceGenerationConfig {
    private final List<EventInfo> eventSequence;
    private final int numberOfSequences;
    private final String sourceApplicationInstanceIdOverride;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<EventInfo> eventSequence;
        private int numberOfSequences;
        private String sourceApplicationInstanceIdOverride;

        private Builder() {
        }

        public Builder eventSequence(EventSequence eventSequence) {
            eventSequence(eventSequence.getOrder());
            return this;
        }

        public Builder eventSequence(EventInfo... eventSequence) {
            eventSequence(Arrays.stream(eventSequence).toList());
            return this;
        }

        public Builder eventSequence(List<EventInfo> eventSequence) {
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
