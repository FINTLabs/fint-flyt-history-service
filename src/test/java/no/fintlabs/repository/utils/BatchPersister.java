package no.fintlabs.repository.utils;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.repository.utils.performance.Timer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static no.fintlabs.repository.utils.performance.DurationFormatter.formatDuration;

@Slf4j
public class BatchPersister {

    private final int batchSize;
    private final Timer timer;

    public BatchPersister(int batchSize) {
        this.batchSize = batchSize;
        this.timer = new Timer();
    }

    public <T> void persistInBatches(JpaRepository<T, Long> repository, List<T> entities) {
        int numberOfEntities = entities.size();
        List<List<T>> entityBatches = Lists.partition(entities, batchSize);
        int numberOfBatches = entityBatches.size();

        log.info("Persisting {} entities in {} batches of size {}", numberOfEntities, numberOfBatches, batchSize);
        timer.start();

        IntStream.range(0, numberOfBatches)
                .forEach(i -> {
                    List<T> batchEntities = entityBatches.get(i);
                    Timer batchTimer = new Timer();
                    batchTimer.start();
                    repository.saveAllAndFlush(batchEntities);
                    Duration batchElapsedTime = batchTimer.getElapsedTime();
                    log.info("Persisted batch {} of {} in {} ({}/s)",
                            i + 1,
                            numberOfBatches,
                            formatDuration(batchElapsedTime),
                            ((long) batchEntities.size() * 1000) / batchElapsedTime.toMillis()
                    );
                });
        Duration elapsedTime = timer.getElapsedTime();
        log.info("Persisted {} entities in {} ({}/s)",
                numberOfEntities,
                formatDuration(elapsedTime),
                String.format("%.2f", (double) numberOfEntities / elapsedTime.toSeconds())
        );
    }

}
