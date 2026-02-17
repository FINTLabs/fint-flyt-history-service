# FINT Flyt History Service

Reactive Spring Boot service that ingests instance-flow events from Kafka, persists them in PostgreSQL, and exposes a
reactive HTTP API for querying history, statistics, and manual remediation workflows. It also answers request/reply
lookups so other Flyt services can fetch the latest metadata for specific instances.

## Highlights

- **Instance-flow timeline API** — non-blocking Spring WebFlux controller that serves summaries, paged timelines, and
aggregate statistics for integrations.
- **Kafka ingestion & request/reply bridges** — consumes every `EventCategory` topic plus request/reply topics for
archive-instance and instance-header lookups.
- **PostgreSQL persistence & Flyway migrations** — writes events through Spring Data JPA with native SQL projections
tuned for analytics queries.
- **Manual remediation workflows** — guarded endpoints let operators append “manually processed/rejected” or “status
overridden” events after validation.
- **Security & observability baked in** — OAuth2 resource server, fine-grained authorization, Actuator health/metrics,
and Prometheus support out of the box.

## Architecture Overview

| Component                                                              | Responsibility                                                                                                                  |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `HistoryController`                                                    | Exposes `/internal/api/instance-flow-tracking` endpoints, performs validation, and shapes HTTP responses.                       |
| `AuthorizationService`                                                 | Delegates to `UserAuthorizationService` to intersect requested source applications with the caller’s access rights.             |
| `EventService`                                                         | Central orchestration for saving events, running summary/statistics queries, mapping projections, and fetching latest metadata. |
| `ManualEventCreationService`                                           | Builds manual events, enforces “latest status must be ERROR,” injects correlation IDs, and persists via `EventService`.         |
| `EventRepository`                                                      | Spring Data JPA repository with tuned SQL for counts, summaries, integration statistics, and request/reply lookups.             |
| `EventListenerConfiguration`                                           | Registers one Kafka listener per `EventCategory`, mapping consumer records into `EventEntity` rows (info and error variants).   |
| `InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration` | Request/reply consumer that returns the latest `InstanceFlowHeaders` for a given instance ID.                                   |
| `ArchiveInstanceIdRequestConsumerConfiguration`                        | Request/reply consumer that resolves the latest archive instance ID for a `SourceApplicationAggregateInstanceId`.               |
| `ValidationErrorsFormattingService`                                    | Folds constraint violations into human-friendly 422 responses for filters and manual action payloads.                           |

## HTTP API

Base path: `/internal/api/instance-flow-tracking`

| Method | Path                                                | Description                                                            | Request body / params                                                                                                                | Response                                                                                       |
|--------|-----------------------------------------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `GET`  | `/statistics/total`                                 | Overall counts per status for all authorized source applications.      | –                                                                                                                                    | `200 OK` with `InstanceStatisticsProjection`.                                                  |
| `GET`  | `/statistics/integrations`                          | Returns a `Slice` of per-integration stats.                            | Query params from `IntegrationStatisticsFilter` plus `page`, `size`, `sort`.                                                         | `200 OK` slice; empty slice when caller lacks access.                                          |
| `GET`  | `/summariesTotalCount`                              | Total number of instance summaries matching the filter.                | `InstanceFlowSummariesFilter` query params (see below).                                                                              | `200 OK` with count; `422` for invalid filters.                                                |
| `GET`  | `/summaries`                                        | Retrieves up to `size` latest summaries matching the filter.           | Same filter params + required `size`.                                                                                                | `200 OK` with array of `InstanceFlowSummary`.                                                  |
| `GET`  | `/events`                                           | Paged timeline for a specific source application aggregate instance.   | Required query params: `sourceApplicationId`, `sourceApplicationIntegrationId`, `sourceApplicationInstanceId`, plus pageable params. | `200 OK` with `Page<Event>`; `403` if unauthorized.                                            |
| `POST` | `/events/instance-manually-processed`               | Appends a manual “processed” event and stores the provided archive ID. | JSON body below.                                                                                                                     | `200 OK` with created event, `404` if no previous event, `400` if latest status isn’t `ERROR`. |
| `POST` | `/events/instance-manually-rejected`                | Marks the instance as manually rejected.                               | JSON body below.                                                                                                                     | Same statuses as above.                                                                        |
| `POST` | `/events/instance-status-overridden-as-transferred` | Overrides the latest status to transferred.                            | JSON body below.                                                                                                                     | Same statuses as above.                                                                        |

`InstanceFlowSummariesFilter` parameters:

- `time.offset.hours`, `time.offset.minutes`, `time.currentPeriod`, or `time.manual.min/max` (ISO date-time) — only one
time filter type can be active.
- `sourceApplicationIds`, `sourceApplicationIntegrationIds`, `sourceApplicationInstanceIds`, `integrationIds` —
multi-value query params.
- `statuses`, `latestStatusEvents`, `storageStatuses`, `associatedEvents` — enums matching `InstanceStatus`,
`EventCategory`, and `InstanceStorageStatus`.
- `destinationIds` — filter by archive instance IDs.
- Custom validators ensure only one status filter (either `statuses` or `latestStatusEvents`) and that manual time
windows have `min <= max`.

Manual action payloads:

```json
{
  "sourceApplicationId": 123,
  "sourceApplicationIntegrationId": "integration-42",
  "sourceApplicationInstanceId": "instance-007",
  "archiveInstanceId": "ark-abc-123"
}
```

`archiveInstanceId` is only required for manually processed events.

Validation failures respond with 422 Unprocessable Entity using the shared formatting service.

## Kafka Integration

- Event ingestion: EventListenerConfiguration registers a listener per EventCategory. INFO events persist headers, timestamps, and application IDs; ERROR events also persist the attached ErrorCollection.
- Request/reply for headers: instance-flow-headers-for-registered-instance topic replies with the latest InstanceFlowHeaders for a given instance ID (retention 5 minutes). Useful when downstream services only know DB IDs.
- Request/reply for archive IDs: archive-instance-id topic replies with the newest archive instance ID for a SourceApplicationAggregateInstanceId.
- Operational defaults: Listeners reuse the shared ListenerConfiguration defaults (auto group ID, bounded poll sizes) and use ErrorHandlerFactory with “no retries, skip failed records.”

## Scheduled Tasks

`StatisticsMetricsPublisher` refreshes Prometheus statistics metrics on a fixed delay
(`novari.flyt.history-service.metrics.refresh-ms`, default `60000` ms).

## Configuration

The service layers Spring profiles (flyt-kafka, flyt-logging, flyt-resource-server, flyt-postgres) and exposes these key properties:

| Property | Description |
| --- | --- |
| fint.application-id | Identifier included in events created by this service (default fint-flyt-history-service). |
| novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time | Retention for instance-processing event topics (default 4d). |
| fint.database.url, fint.database.username, fint.database.password | PostgreSQL JDBC connection supplied via secrets/environment. |
| spring.kafka.bootstrap-servers | Kafka cluster endpoint; application-local-staging.yaml defaults to localhost:9092. |
| spring.security.oauth2.resourceserver.jwt.issuer-uri | Authority used for JWT validation. |
| no.novari.flyt.resource-server.security.api.internal.authorized-org-id-role-pairs-json | Mapping of org IDs to roles that may call the internal API. |
| server.max-http-request-header-size | Raised to 40KB to handle large JWTs. |
| spring.jackson.time-zone | Forces JSON serialization to UTC. |

Secrets referenced in the base Kustomize manifests must include DB credentials and OAuth client metadata.

## Running Locally

Prerequisites: Java 21+, Gradle wrapper (bundled), Docker (for Postgres), and access to a Kafka broker (local or shared).

1. Start Postgres (detached container): ./start-postgres
2. Provide a Kafka broker on localhost:9092 (e.g., via the Flyt dev cluster or a local stack).
3. Export SPRING_PROFILES_ACTIVE=local-staging to pick up application-local-staging.yaml.

Useful commands:

./gradlew clean build                  # compile + unit tests
./gradlew bootRun                      # run WebFlux app with local profiles
./gradlew test                         # run default test suite
./gradlew performanceTest              # run tests tagged @Tag("performance")

Flyway migrations run at startup; ensure the configured schema exists (local profile uses fintlabs_no).

## Deployment

- kustomize/base/ holds shared Flyt resources (Application, secrets, config maps, DB connection).
- kustomize/overlays/<org>/<env>/ contains per-organization/environment patches (namespace, Kafka topics, OAuth issuers, DB secrets).
- kustomize/templates/ stores overlay templates; regenerate overlays after edits with script/render-overlay.sh, which emits kustomization.yaml files in-place.

## Security

- Runs as an OAuth2 resource server (JWT) and only exposes internal APIs guarded by novari.flyt.resource-server.security.api.internal.
- AuthorizationService relies on UserAuthorizationService to intersect requested source applications with caller entitlements before returning data or executing manual actions.
- Manual event endpoints validate payloads, re-check authorization, ensure the latest status is ERROR, and return 404/400 for inconsistent histories.

## Observability & Operations

- Actuator readiness at /actuator/health and Prometheus metrics at /actuator/prometheus (Micrometer Prometheus registry enabled).
- Custom statistics metrics are published for Grafana dashboards (see Metrics section below).
- Logs follow Spring defaults with Reactor context for correlation IDs; Kafka consumers attach origin app IDs for traceability.
- Event table indexes (timestamp, composite source application keys) keep summary queries fast; Flyway migrations (src/main/resources/db/migration) manage schema evolution.

## Metrics

The service publishes the same statistics data from the HTTP API as Prometheus gauges for Grafana dashboards.

Metrics:

- `flyt_history_instance_count` — total counts by status (tags: `status`)
- `flyt_history_integration_count` — counts by integration and status (tags: `integration_id`, `status`)

Status tag values: `total`, `in_progress`, `transferred`, `aborted`, `failed`.

Scrape endpoint: `/actuator/prometheus`

Refresh interval: `novari.flyt.history-service.metrics.refresh-ms` (default `60000`).

## Development Tips

- When adding a new EventCategory, decide whether Kafka should auto-create a listener (createKafkaListener=true) and update downstream categorization logic.
- Filter DTOs are validated via custom annotations (OnlyOneStatusFilter, OnlyOneTimeFilterType); extend validators when you add new filter types.
- Reuse ValidationErrorsFormattingService for consistent 422 payloads on new controllers.
- Request/reply topics keep only 5 minutes of history; use them for fresh data, not bulk exports.
- Use performanceTest task for heavier queries to avoid mixing them with the default test suite.

## Contributing

1. Create a feature branch.
2. Run ./gradlew test (and performanceTest if applicable) before opening a PR.
3. If you touch Kustomize overlays or templates, rerun script/render-overlay.sh and commit the generated changes.
4. Describe any new filters, Kafka topics, or manual actions in this README to keep operators aligned.

FINT Flyt History Service is maintained by the FINT Flyt team. Reach out via the internal Slack channel or open an issue in this repository with questions or enhancement ideas.
