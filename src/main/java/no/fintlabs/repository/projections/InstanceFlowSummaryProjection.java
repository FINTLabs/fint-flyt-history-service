package no.fintlabs.repository.projections;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.kafka.support.JavaUtils;

import java.time.OffsetDateTime;
import java.util.StringJoiner;

@Getter
@Builder
@EqualsAndHashCode
public class InstanceFlowSummaryProjection {
    Long sourceApplicationId;
    String sourceApplicationIntegrationId;
    String sourceApplicationInstanceId;
    Long integrationId;
    Long latestInstanceId;
    OffsetDateTime latestUpdate;
    String latestStatusEventName;
    String latestStorageStatusEventName;
    String destinationInstanceIds;

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationId, sourceApplicationId -> joiner.add("sourceApplicationId=" + sourceApplicationId)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationIntegrationId, sourceApplicationIntegrationId ->
                        joiner.add("sourceApplicationIntegrationId=" + sourceApplicationIntegrationId)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                sourceApplicationInstanceId, sourceApplicationInstanceId ->
                        joiner.add("sourceApplicationInstanceId=" + sourceApplicationInstanceId)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                integrationId, integrationId -> joiner.add("integrationId=" + integrationId)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestInstanceId, latestInstanceId -> joiner.add("latestInstanceId=" + latestInstanceId)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestUpdate, latestUpdate -> joiner.add("latestUpdate=" + latestUpdate)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestStatusEventName, latestStatusEventName -> joiner.add("latestStatusEventName=" + latestStatusEventName)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                latestStorageStatusEventName, latestStorageStatusEventName -> joiner.add("latestStorageStatusEventName=" + latestStorageStatusEventName)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                destinationInstanceIds, destinationInstanceIds -> joiner.add("destinationInstanceIds=" + destinationInstanceIds)
        );
        return joiner.toString();
    }
}
