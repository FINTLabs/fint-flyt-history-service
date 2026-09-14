package no.novari.flyt.history.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A value and its display label.")
data class Selectable<T>(
    val value: T,
    val label: String,
)
