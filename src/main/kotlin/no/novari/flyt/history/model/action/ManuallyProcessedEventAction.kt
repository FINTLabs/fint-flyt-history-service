package no.novari.flyt.history.model.action

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId

data class ManuallyProcessedEventAction(
    @field:Positive
    override val sourceApplicationId: Long = 0,
    @field:NotEmpty
    override val sourceApplicationIntegrationId: String = "",
    @field:NotEmpty
    override val sourceApplicationInstanceId: String = "",
    @field:NotEmpty
    val archiveInstanceId: String = "",
) : SourceApplicationAggregateInstanceId
