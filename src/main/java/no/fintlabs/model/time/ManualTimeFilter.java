package no.fintlabs.model.time;

import lombok.*;
import no.fintlabs.validation.MinTimestampBeforeMaxTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@MinTimestampBeforeMaxTimestamp
public class ManualTimeFilter {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime min;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime max;

}
