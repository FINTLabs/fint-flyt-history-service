package no.fintlabs.repository.filters;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Optional;

@Builder
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

}
