package no.novari.flyt.history

import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_CLIENT_API
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("$INTERNAL_CLIENT_API/archive-instance-id")
class ArchiveInstanceIdInternalClientController(
    private val eventService: EventService,
) {
    @GetMapping
    fun getLatestArchiveInstanceId(
        @RequestParam sourceApplicationId: Long,
        @RequestParam(required = false) sourceApplicationIntegrationId: String?,
        @RequestParam sourceApplicationInstanceId: String,
    ): String =
        eventService.findLatestArchiveInstanceId(
            sourceApplicationId,
            sourceApplicationIntegrationId,
            sourceApplicationInstanceId,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}
