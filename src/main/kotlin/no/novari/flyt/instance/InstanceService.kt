package no.novari.flyt.instance

import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class InstanceService(
    private val instanceRepository: InstanceRepository,
    private val instanceMappingService: InstanceMappingService,
    private val clock: Clock,
) {
    @Transactional
    fun save(instanceObjectDto: InstanceObjectDto): InstanceObjectDto =
        instanceMappingService.toInstanceObjectDto(
            instanceRepository.save(instanceMappingService.toInstanceObject(instanceObjectDto)),
        )

    @Transactional
    fun deleteById(instanceId: Long) {
        instanceRepository.deleteById(instanceId)
    }

    /** Returns false when the instance is already gone, for example deleted by instance-service. */
    @Transactional
    fun deleteIfExists(instanceId: Long): Boolean {
        val instance = instanceRepository.findForDeletionById(instanceId) ?: return false
        instanceRepository.delete(instance)
        return true
    }

    @Transactional(readOnly = true)
    fun getById(instanceId: Long): InstanceObjectDto =
        instanceMappingService.toInstanceObjectDto(instanceRepository.getReferenceById(instanceId))

    @Transactional(readOnly = true)
    fun getAllOlderThan(days: Int): List<InstanceObjectDto> {
        val thresholdDate = Instant.now(clock).minus(days.toLong(), ChronoUnit.DAYS)
        return instanceRepository.findAllOlderThan(thresholdDate).map(instanceMappingService::toInstanceObjectDto)
    }
}
