package no.novari.flyt.history.repository.utils

import no.novari.flyt.history.repository.utils.performance.DurationFormatter.formatDuration
import no.novari.flyt.history.repository.utils.performance.Timer
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository

class BatchPersister<T>(
    private val repository: JpaRepository<T, Long>,
    private val batchSize: Int,
) {
    fun persistInBatches(entities: List<T>) {
        val numberOfEntities = entities.size
        val entityBatches = entities.chunked(batchSize)
        val numberOfBatches = entityBatches.size

        log.debug("Persisting {} entities in {} batches of size {}", numberOfEntities, numberOfBatches, batchSize)
        val timer = Timer.start()

        entityBatches.forEachIndexed { index, batchEntities ->
            val batchTimer = Timer.start()
            repository.saveAllAndFlush(batchEntities)
            val batchElapsedTime = batchTimer.elapsedTime
            log.debug(
                "Persisted batch {} of {} in {} ({}/s)",
                index + 1,
                numberOfBatches,
                formatDuration(batchElapsedTime),
                ((batchEntities.size.toLong()) * 1000) / batchElapsedTime.toMillis(),
            )
        }

        val elapsedTime = timer.elapsedTime
        log.debug(
            "Persisted {} entities in {} ({}/s)",
            numberOfEntities,
            formatDuration(elapsedTime),
            String.format("%.2f", numberOfEntities.toDouble() * 1000 / elapsedTime.toMillis()),
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(BatchPersister::class.java)
    }
}
