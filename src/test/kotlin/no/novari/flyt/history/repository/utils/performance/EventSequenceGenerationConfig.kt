package no.novari.flyt.history.repository.utils.performance

import no.novari.flyt.history.model.event.EventCategory

class EventSequenceGenerationConfig(
    val eventSequence: List<EventCategory>,
    val numberOfSequences: Int,
    val sourceApplicationInstanceIdOverride: String? = null,
) {
    constructor(
        eventSequence: EventSequence,
        numberOfSequences: Int,
        sourceApplicationInstanceIdOverride: String?,
    ) : this(eventSequence.order, numberOfSequences, sourceApplicationInstanceIdOverride)

    constructor(
        eventSequence: List<EventCategory>,
        numberOfSequences: Int,
    ) : this(eventSequence, numberOfSequences, null)

    constructor(
        eventSequence: EventSequence,
        numberOfSequences: Int,
    ) : this(eventSequence.order, numberOfSequences)

    override fun toString(): String {
        return "EventSequenceGenerationConfig(eventSequence=$eventSequence, numberOfSequences=$numberOfSequences, " +
            "sourceApplicationInstanceIdOverride=$sourceApplicationInstanceIdOverride)"
    }
}
