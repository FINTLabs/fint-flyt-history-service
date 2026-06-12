package no.novari.flyt.history

import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ErrorScrubService(
    private val eventRepository: EventRepository,
    @param:Value("\${novari.flyt.history-service.retention.scrub-batch-size}")
    private val scrubBatchSize: Int,
) {
    @Transactional
    fun scrubByInstanceFlowHeaders(headers: InstanceFlowHeaders) {
        val sourceApplicationId = requireNotNull(headers.sourceApplicationId)
        val sourceApplicationIntegrationId = requireNotNull(headers.sourceApplicationIntegrationId)
        val sourceApplicationInstanceId = requireNotNull(headers.sourceApplicationInstanceId)

        while (true) {
            val eventIds =
                findUnscrubbedEventSlice(
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                    sourceApplicationInstanceId = sourceApplicationInstanceId,
                ).content
                    .map { it.id }

            if (eventIds.isEmpty()) {
                return
            }

            eventRepository.scrubErrorArgsByEventIds(eventIds)
            eventRepository.scrubByEventIds(eventIds)
        }
    }

    private fun findUnscrubbedEventSlice(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
    ) = eventRepository.findUnscrubbedBySourceApplicationAggregateInstanceId(
        sourceApplicationId = sourceApplicationId,
        sourceApplicationIntegrationId = sourceApplicationIntegrationId,
        sourceApplicationInstanceId = sourceApplicationInstanceId,
        pageable =
            PageRequest.of(
                0,
                scrubBatchSize,
                Sort.by(Sort.Direction.ASC, "id"),
            ),
    )
}
