package no.novari.flyt.history.model.response

import no.novari.flyt.history.model.event.Event

data class EventPageResponse(
    val content: List<Event>,
    val last: Boolean,
)
