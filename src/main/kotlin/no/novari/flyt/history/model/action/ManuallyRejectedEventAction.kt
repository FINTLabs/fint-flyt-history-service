package no.novari.flyt.history.model.action

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId

@Schema(description = "Request to mark an instance as manually rejected.")
data class ManuallyRejectedEventAction(
    @field:Positive
    override val sourceApplicationId: Long = 0,
    @field:NotEmpty
    override val sourceApplicationInstanceId: String = "",
    @field:NotEmpty
    override val sourceApplicationIntegrationId: String = "",
    @field:Schema(
        description = "Optional explanation for the manual status. Stored as null if blank. No fixed character limit.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true,
    )
    val comment: String? = null,
) : SourceApplicationAggregateInstanceId
