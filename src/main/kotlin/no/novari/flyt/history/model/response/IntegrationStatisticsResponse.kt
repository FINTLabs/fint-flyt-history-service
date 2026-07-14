package no.novari.flyt.history.model.response

import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection

data class IntegrationStatisticsResponse(
    val content: List<IntegrationStatisticsProjection>,
)
