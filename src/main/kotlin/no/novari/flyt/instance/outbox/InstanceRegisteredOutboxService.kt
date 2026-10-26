package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegisteredOutboxService(
    private val repository: InstanceRegisteredOutboxRepository,
) {
    @Transactional
    fun claimNext(): InstanceRegisteredOutboxClaim? = repository.claimNext()

    @Transactional
    fun complete(claim: InstanceRegisteredOutboxClaim): Boolean = repository.complete(claim)

    @Transactional
    fun fail(
        claim: InstanceRegisteredOutboxClaim,
        errorType: String,
    ): Boolean = repository.fail(claim, errorType)

    @Transactional(readOnly = true)
    fun statistics(): InstanceRegisteredOutboxStatistics = repository.statistics()
}
