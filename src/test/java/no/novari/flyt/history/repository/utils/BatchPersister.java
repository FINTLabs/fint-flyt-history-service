package no.novari.flyt.history.repository.utils;

import lombok.extern.slf4j.Slf4j;
import no.novari.flyt.history.repository.utils.performance.Timer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static no.novari.flyt.history.repository.utils.performance.DurationFormatter.formatDuration;

@Slf4j
public class BatchPersister<T> {

    private final JpaRepository<T, Long> repository;
    private final int batchSize;

    public BatchPersister(
            JpaRepository<T, Long> repository,
            int batchSize
    ) {
        this.repository = repository;
        this.batchSize = batchSize;
    }

    public void persistInBatches(List<T> entities) {
        int numberOfEntities = entities.size();
        List<List<T>> entityBatches = Lists.partition(entities, batchSize);
        int numberOfBatches = entityBatches.size();

        log.debug("Persisting {} entities in {} batches of size {}", numberOfEntities, numberOfBatches, batchSize);
        Timer timer = Timer.start();

        IntStream.range(0, numberOfBatches)
                .forEach(i -> {
                    List<T> batchEntities = entityBatches.get(i);
                    Timer batchTimer = Timer.start();
                    repository.saveAllAndFlush(batchEntities);
                    Duration batchElapsedTime = batchTimer.getElapsedTime();
                    log.debug("Persisted batch {} of {} in {} ({}/s)",
                            i + 1,
                            numberOfBatches,
                            formatDuration(batchElapsedTime),
                            ((long) batchEntities.size() * 1000) / batchElapsedTime.toMillis()
                    );
                });
        Duration elapsedTime = timer.getElapsedTime();
        log.debug("Persisted {} entities in {} ({}/s)",
                numberOfEntities,
                formatDuration(elapsedTime),
                String.format("%.2f", (double) numberOfEntities * 1000 / elapsedTime.toMillis())
        );
    }

}
