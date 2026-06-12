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

        var pageNumber = 0

        do {
            val eventPage =
                findEventPage(
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                    sourceApplicationInstanceId = sourceApplicationInstanceId,
                    pageNumber = pageNumber,
                )

            val eventIds = eventPage.content.map { it.id }
            if (eventIds.isNotEmpty()) {
                eventRepository.scrubErrorArgsByEventIds(eventIds)
                eventRepository.scrubByEventIds(eventIds)
            }

            pageNumber++
        } while (eventPage.hasNext())
    }

    private fun findEventPage(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        pageNumber: Int,
    ) = eventRepository.findAllBySourceApplicationAggregateInstanceId(
        sourceApplicationId = sourceApplicationId,
        sourceApplicationIntegrationId = sourceApplicationIntegrationId,
        sourceApplicationInstanceId = sourceApplicationInstanceId,
        pageable =
            PageRequest.of(
                pageNumber,
                scrubBatchSize,
                Sort.by(Sort.Direction.ASC, "id"),
            ),
    )
}
