package no.novari.flyt.history.model.action

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId

@Schema(description = "Request to override an instance status as transferred.")
data class InstanceStatusTransferredOverrideAction(
    @field:Positive
    override val sourceApplicationId: Long = 0,
    @field:NotEmpty
    override val sourceApplicationInstanceId: String = "",
    @field:NotEmpty
    override val sourceApplicationIntegrationId: String = "",
    @field:Size(max = 250)
    @field:Schema(
        description = "Optional plain-text comment for this manual status. Empty or blank text is stored as null.",
        maxLength = 250,
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val comment: String? = null,
) : SourceApplicationAggregateInstanceId
