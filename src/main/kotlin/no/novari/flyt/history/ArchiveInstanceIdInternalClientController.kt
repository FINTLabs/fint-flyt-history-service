package no.novari.flyt.history

import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_CLIENT_API
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$INTERNAL_CLIENT_API/archive-instance-id")
class ArchiveInstanceIdInternalClientController(
    private val eventService: EventService,
) {
    // The archive instance ID is returned as plain text, which is what HistoryClient in flyt-gateway-starter and
    // flyt-web-instance-gateway reads. No match is an expected answer, so it is 404 without a body or a log entry.
    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getLatestArchiveInstanceId(
        @RequestParam sourceApplicationId: Long,
        @RequestParam(required = false) sourceApplicationIntegrationId: String?,
        @RequestParam sourceApplicationInstanceId: String,
    ): ResponseEntity<String> =
        eventService
            .findLatestArchiveInstanceId(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                sourceApplicationInstanceId,
            )?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}
