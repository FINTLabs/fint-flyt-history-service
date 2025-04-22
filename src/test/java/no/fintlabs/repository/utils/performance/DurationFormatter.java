package no.fintlabs.repository.utils.performance;

import java.time.Duration;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class DurationFormatter {
    public static String formatDuration(Duration duration) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        performIfPositive(duration.toMinutes(), v -> stringJoiner.add(String.format("%2dm", v)));
        performIfPositive(duration.toSecondsPart(), v -> stringJoiner.add(String.format("%2ds", v)));
        performIfPositive(duration.toMillisPart(), v -> stringJoiner.add(String.format("%3dms", v)));
        return stringJoiner.toString();
    }

    private static void performIfPositive(long value, Consumer<Long> valueConsumer) {
        if (value > 0) {
            valueConsumer.accept(value);
        }
    }
}
