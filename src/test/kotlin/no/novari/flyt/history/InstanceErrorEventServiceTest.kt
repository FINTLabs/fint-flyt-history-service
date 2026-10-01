package no.novari.flyt.history

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class InstanceErrorEventServiceTest {
    private val eventRepository: EventRepository = mock()
    private val service = InstanceErrorEventService(eventRepository, InstanceFlowHeadersMappingService())
    private val timestamp = Instant.parse("2026-01-15T10:20:30Z")

    @ParameterizedTest
    @EnumSource(InstanceErrorOrigin::class)
    fun `maps every instance error origin to its event category`(origin: InstanceErrorOrigin) {
        val headers = instanceFlowHeaders()
        val errors = ErrorCollection(listOf(Error("test-error", mapOf("key" to "value"))))

        service.registerError(headers, InstanceErrorEvent(name = origin, errors = errors), timestamp, "source-app")

        val saved = savedEvent()
        assertThat(saved.name).isEqualTo(expectedCategory(origin).eventName)
        assertThat(saved.type).isEqualTo(EventType.ERROR)
        assertThat(saved.errors).hasSize(1)
    }

    @Test
    fun `legacy errors retain category code arguments timestamp application and headers`() {
        val headers = instanceFlowHeaders()
        val errors = ErrorCollection(listOf(Error("test-error", mapOf("present" to "value", "missing" to null))))

        service.registerError(headers, EventCategory.INSTANCE_REGISTRATION_ERROR, errors, timestamp, "source-app")

        val saved = savedEvent()
        assertThat(saved.name).isEqualTo(EventCategory.INSTANCE_REGISTRATION_ERROR.eventName)
        assertThat(saved.type).isEqualTo(EventType.ERROR)
        assertThat(saved.timestamp).isEqualTo(timestamp.atOffset(ZoneOffset.UTC))
        assertThat(saved.applicationId).isEqualTo("source-app")
        assertThat(saved.instanceFlowHeaders?.sourceApplicationId).isEqualTo(headers.sourceApplicationId)
        assertThat(saved.instanceFlowHeaders?.sourceApplicationIntegrationId)
            .isEqualTo(headers.sourceApplicationIntegrationId)
        assertThat(saved.instanceFlowHeaders?.sourceApplicationInstanceId)
            .isEqualTo(headers.sourceApplicationInstanceId)
        assertThat(saved.instanceFlowHeaders?.correlationId).isEqualTo(headers.correlationId)
        assertThat(saved.errors.single().errorCode).isEqualTo("test-error")
        assertThat(saved.errors.single().args).containsEntry("present", "value").containsEntry("missing", "")
    }

    @Test
    fun `legacy entry rejects info categories without saving`() {
        assertThatThrownBy {
            service.registerError(
                instanceFlowHeaders(),
                EventCategory.INSTANCE_RECEIVED,
                ErrorCollection(null),
                timestamp,
                "source-app",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        verifyNoInteractions(eventRepository)
    }

    @Test
    fun `missing errors are saved as an empty collection`() {
        service.registerError(
            instanceFlowHeaders(),
            EventCategory.INSTANCE_RECEIVAL_ERROR,
            ErrorCollection(null),
            timestamp,
            "source-app",
        )

        assertThat(savedEvent().errors).isEmpty()
    }

    @Test
    fun `missing errors in instance error event are saved as an empty collection`() {
        service.registerError(
            instanceFlowHeaders(),
            InstanceErrorEvent(name = InstanceErrorOrigin.RECEIVAL, errors = ErrorCollection(null)),
            timestamp,
            "source-app",
        )

        assertThat(savedEvent().errors).isEmpty()
    }

    private fun savedEvent(): EventEntity {
        val eventCaptor = argumentCaptor<EventEntity>()
        verify(eventRepository).save(eventCaptor.capture())
        return eventCaptor.firstValue
    }

    private fun expectedCategory(origin: InstanceErrorOrigin): EventCategory =
        when(origin) {
            InstanceErrorOrigin.RECEIVAL -> EventCategory.INSTANCE_RECEIVAL_ERROR
            InstanceErrorOrigin.REGISTRATION -> EventCategory.INSTANCE_REGISTRATION_ERROR
            InstanceErrorOrigin.RETRY_REQUEST -> EventCategory.INSTANCE_RETRY_REQUEST_ERROR
            InstanceErrorOrigin.MAPPING -> EventCategory.INSTANCE_MAPPING_ERROR
            InstanceErrorOrigin.DISPATCHING -> EventCategory.INSTANCE_DISPATCHING_ERROR
        }

    private fun instanceFlowHeaders(): InstanceFlowHeaders =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("sa-integration-1")
            .sourceApplicationInstanceId("sa-instance-1")
            .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
            .integrationId(100L)
            .build()
}
