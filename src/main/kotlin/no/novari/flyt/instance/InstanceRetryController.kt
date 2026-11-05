package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.EntityNotFoundException
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping(INTERNAL_API)
@ConditionalOnInstanceFunctionality
@Tag(name = "Instance retries", description = "Retry processing for Flyt instances.")
class InstanceRetryController(
    private val instanceRetryService: InstanceRetryService,
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("handlinger/instanser/{instanceId}/prov-igjen")
    @Operation(summary = "Retry an instance", operationId = "retryInstance")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Retry request accepted for publication"),
            ApiResponse(responseCode = "404", description = "Instance not found"),
            ApiResponse(responseCode = "500", description = "Retry request failed"),
        ],
    )
    fun retry(
        @PathVariable instanceId: Long,
    ): ResponseEntity<Void> {
        try {
            instanceRetryService.retry(instanceId)
        } catch (_: EntityNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find instance with id='$instanceId'")
        } catch (failure: NoInstanceFlowHeadersException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, failure.message)
        } catch (failure: Exception) {
            logFailure(instanceId, failure)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("handlinger/instanser/prov-igjen/batch")
    @Operation(summary = "Retry multiple instances", operationId = "retryInstances")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Batch retry request processed"),
            ApiResponse(responseCode = "500", description = "Batch retry interrupted"),
        ],
    )
    fun retry(
        @RequestBody instanceIds: List<Long>,
    ): ResponseEntity<Void> {
        instanceIds.forEach { instanceId ->
            try {
                instanceRetryService.retry(instanceId)
            } catch (failure: Exception) {
                logFailure(instanceId, failure)
                if (failure is InterruptedException || Thread.currentThread().isInterrupted) {
                    Thread.currentThread().interrupt()
                    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Batch retry interrupted")
                }
            }
        }
        return ResponseEntity.ok().build()
    }

    private fun logFailure(
        instanceId: Long,
        failure: Exception,
    ) {
        logger.error(safeDiagnosticCause(failure)) { "Retry request failed for instanceId=$instanceId" }
    }
}
