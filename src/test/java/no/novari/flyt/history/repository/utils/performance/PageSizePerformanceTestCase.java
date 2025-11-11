package no.novari.flyt.history.repository.utils.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.kafka.support.JavaUtils;

import java.time.Duration;
import java.util.StringJoiner;

@Getter
@AllArgsConstructor
public class PageSizePerformanceTestCase {
    private final int requestedMaxSize;
    private final int expectedSize;
    private final Duration maxElapsedTime;

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                requestedMaxSize, requestedSize -> joiner.add("requestedSize=" + requestedSize)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                expectedSize, expectedSize -> joiner.add("expectedSize=" + expectedSize)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                maxElapsedTime, maxElapsedTime -> joiner.add("maxElapsedTime=" + maxElapsedTime)
        );
        return joiner.toString();
    }

}
