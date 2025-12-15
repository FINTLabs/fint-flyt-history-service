package no.novari.flyt.history.repository.filters;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import org.springframework.kafka.support.JavaUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.StringJoiner;

@Builder
@EqualsAndHashCode
public class TimeQueryFilter {

    public static final TimeQueryFilter EMPTY =
            new TimeQueryFilter(null, null);

    private final OffsetDateTime latestStatusTimestampMin;
    private final OffsetDateTime latestStatusTimestampMax;

    public Optional<OffsetDateTime> getLatestStatusTimestampMin() {
        return Optional.ofNullable(latestStatusTimestampMin);
    }

    public Optional<OffsetDateTime> getLatestStatusTimestampMax() {
        return Optional.ofNullable(latestStatusTimestampMax);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestStatusTimestampMin, latestStatusTimestampMin -> joiner.add("latestStatusTimestampMin=" + latestStatusTimestampMin)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestStatusTimestampMax, latestStatusTimestampMax -> joiner.add("latestStatusTimestampMax=" + latestStatusTimestampMax)
        );
        return joiner.toString();
    }

}
