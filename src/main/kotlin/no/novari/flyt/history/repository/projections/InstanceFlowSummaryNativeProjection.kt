package no.novari.flyt.history.repository.projections

import java.time.Instant

interface InstanceFlowSummaryNativeProjection {
    fun getSourceApplicationId(): Long?

    fun getSourceApplicationIntegrationId(): String?

    fun getSourceApplicationInstanceId(): String?

    fun getIntegrationId(): Long?

    fun getLatestInstanceId(): Long?

    fun getLatestUpdate(): Instant?

    fun getLatestStatusEventName(): String?

    fun getLatestStorageStatusEventName(): String?

    fun getDestinationInstanceIds(): String?
}
