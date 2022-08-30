package no.fintlabs.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntegrationStatisticsWrapper {
    private final Map<String, IntegrationStatistics> statisticsPerIntegrationId;
}
