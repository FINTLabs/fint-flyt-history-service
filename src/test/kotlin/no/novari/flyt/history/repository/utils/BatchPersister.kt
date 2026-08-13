package no.novari.flyt.history.repository.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.repository.utils.performance.DurationFormatter.formatDuration
import no.novari.flyt.history.repository.utils.performance.Timer
import org.springframework.data.jpa.repository.JpaRepository

class BatchPersister<T>(
    private val repository: JpaRepository<T, Long>,
    private val batchSize: Int,
) {
    fun persistInBatches(entities: List<T>) {
        val numberOfEntities = entities.size
        val entityBatches = entities.chunked(batchSize)
        val numberOfBatches = entityBatches.size

        log.atDebug {
            message = "Persisting {} entities in {} batches of size {}"
            arguments = arrayOf(numberOfEntities, numberOfBatches, batchSize)
        }
        val timer = Timer.start()

        entityBatches.forEachIndexed { index, batchEntities ->
            val batchTimer = Timer.start()
            repository.saveAllAndFlush(batchEntities)
            val batchElapsedTime = batchTimer.elapsedTime
            log.atDebug {
                message = "Persisted batch {} of {} in {} ({}/s)"
                arguments =
                    arrayOf(
                        index + 1,
                        numberOfBatches,
                        formatDuration(batchElapsedTime),
                        ((batchEntities.size.toLong()) * 1000) / batchElapsedTime.toMillis(),
                    )
            }
        }

        val elapsedTime = timer.elapsedTime
        log.atDebug {
            message = "Persisted {} entities in {} ({}/s)"
            arguments =
                arrayOf(
                    numberOfEntities,
                    formatDuration(elapsedTime),
                    String.format("%.2f", numberOfEntities.toDouble() * 1000 / elapsedTime.toMillis()),
                )
        }
    }

    private companion object {
        private val log = KotlinLogging.logger {}
    }
}
