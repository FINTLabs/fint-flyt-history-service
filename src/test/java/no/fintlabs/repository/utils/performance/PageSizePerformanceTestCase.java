package no.fintlabs.repository.utils.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.kafka.support.JavaUtils;

import java.time.Duration;
import java.util.StringJoiner;

@Getter
@AllArgsConstructor
public class PageSizePerformanceTestCase {
    private final int pageSize;
    private final Duration maxElapsedTime;

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                pageSize, pageSize -> joiner.add("pageSize=" + pageSize)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                maxElapsedTime, maxElapsedTime -> joiner.add("maxElapsedTime=" + maxElapsedTime)
        );
        return joiner.toString();
    }

}
