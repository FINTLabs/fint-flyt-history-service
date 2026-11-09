# FINT Flyt History Service

Spring Boot service that ingests instance-flow events from Kafka, persists them in PostgreSQL, and exposes an internal
HTTP API for querying history, statistics, and manual remediation workflows. It also answers request/reply lookups so
other Flyt services can fetch the latest metadata for specific instances.

## Highlights

- **Instance-flow timeline API** — Spring MVC controller that serves summaries, paged timelines, and aggregate
statistics for integrations.
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
| `HistoryController`                                                    | Exposes `/api/intern/instance-flow-tracking` endpoints, performs validation, and shapes HTTP responses.                       |
| `AuthorizationService`                                                 | Delegates to `UserAuthorizationService` to intersect requested source applications with the caller’s access rights.             |
| `EventService`                                                         | Central orchestration for saving events, running summary/statistics queries, mapping projections, and fetching latest metadata. |
| `ManualEventCreationService`                                           | Builds manual events, enforces “latest status must be ERROR,” injects correlation IDs, and persists via `EventService`.         |
| `EventRepository`                                                      | Spring Data JPA repository with tuned SQL for counts, summaries, integration statistics, and request/reply lookups.             |
| `EventListenerConfiguration`                                           | Registers one Kafka listener per `EventCategory`, mapping consumer records into `EventEntity` rows (info and error variants).   |
| `InstanceFlowHeadersForRegisteredInstanceRequestConsumerConfiguration` | Request/reply consumer that returns the latest `InstanceFlowHeaders` for a given instance ID.                                   |
| `ArchiveInstanceIdRequestConsumerConfiguration`                        | Request/reply consumer that resolves the latest archive instance ID for a `SourceApplicationAggregateInstanceId`.               |
| `ValidationErrorsFormattingService`                                    | Folds constraint violations into human-friendly 422 responses for filters and manual action payloads.                           |

## HTTP API

Base path: `/api/intern/instance-flow-tracking`

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

### OpenAPI Documentation

Swagger UI and the generated OpenAPI specification are available only through direct service access, such as a
Kubernetes port-forward. Their paths sit outside the external ingress route for `/api/intern/instance-flow-tracking`:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- OpenAPI YAML: `/v3/api-docs.yaml`

For the FINTLabs beta deployment:

```shell
kubectl -n fintlabs-no port-forward service/fint-flyt-history-service 8080:8080
```

Swagger UI is then available at `http://localhost:8080/beta/fintlabs-no/swagger-ui.html`, and OpenAPI JSON at
`http://localhost:8080/beta/fintlabs-no/v3/api-docs`.

## Kafka Integration

- Event ingestion: EventListenerConfiguration registers a listener per EventCategory. INFO events persist headers, timestamps, and application IDs; ERROR events also persist the attached ErrorCollection.
- Request/reply for headers: instance-flow-headers-for-registered-instance topic replies with the latest InstanceFlowHeaders for a given instance ID (retention 5 minutes). Useful when downstream services only know DB IDs.
- Request/reply for archive IDs: archive-instance-id topic replies with the newest archive instance ID for a SourceApplicationAggregateInstanceId.
- Operational defaults: Listeners reuse the shared ListenerConfiguration defaults (auto group ID, bounded poll sizes) and use ErrorHandlerFactory with “no retries, skip failed records.”

## Scheduled Tasks

`StatisticsMetricsPublisher` refreshes Prometheus statistics metrics on a fixed delay
(`novari.flyt.history-service.metrics.refresh-ms`, default `60000` ms).

`ScheduledErrorScrubService` runs daily after an initial 2 minutes delay and scrubs old, unscrubbed events in batches.

## Configuration

The service layers Spring profiles (flyt-kafka, flyt-logging, flyt-web-resource-server,
flyt-authorization-client, flyt-postgres) and exposes these key properties:

| Property                                                                                   | Description                                                                                |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| fint.application-id                                                                        | Identifier included in events created by this service (default fint-flyt-history-service). |
| novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time          | Retention for instance-processing event topics (default 4d).                               |
| novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled                     | Enables listeners for the five legacy error topics (default true).                         |
| novari.flyt.history-service.retention.time-to-keep-error-details-in-days                   | Number of days to keep error detail values before scheduled scrub.                         |
| novari.flyt.history-service.retention.scrub-batch-size                                     | Maximum number of events scrubbed per database transaction.                                |
| novari.flyt.instance-functionality.enabled                                                 | Enables instance registration from instance-received (default false).                      |
| novari.flyt.instance.database.schema                                                       | Instance schema override; derived from the database user when empty.                       |
| fint.database.url, fint.database.username, fint.database.password                          | PostgreSQL JDBC connection supplied via secrets/environment.                               |
| spring.kafka.bootstrap-servers                                                             | Kafka cluster endpoint; application-local-staging.yaml defaults to localhost:9092.         |
| spring.security.oauth2.resourceserver.jwt.issuer-uri                                       | Authority used for JWT validation.                                                         |
| fint.flyt.authorization.sso.client-id / client-secret                                      | OAuth2 client credentials for authorization-service.                                       |
| no.novari.flyt.web-resource-server.security.api.internal.authorized-org-id-role-pairs-json | Mapping of org IDs to roles that may call the internal API.                                |
| server.max-http-request-header-size                                                        | Raised to 40KB to handle large JWTs.                                                       |
| spring.jackson.time-zone                                                                   | Forces JSON serialization to UTC.                                                          |

Secrets referenced in the base Kustomize manifests must include DB credentials and OAuth client metadata.

## Running Locally

Prerequisites: Java 25+, Gradle wrapper (bundled), and Docker.

1. Start Postgres on localhost:5436 and Kafka on localhost:9092: `docker compose up -d`
2. Export SPRING_PROFILES_ACTIVE=local-staging to pick up application-local-staging.yaml.

Add `--profile tools` to also start Kafdrop on http://localhost:19000. Kafka topics and the database are empty on every start.

Useful commands:

./gradlew clean build                  # compile + unit tests
./gradlew bootRun                      # run Spring MVC app with local profiles
./gradlew test                         # run default test suite
./gradlew performanceTest              # run tests tagged @Tag("performance")

## Instance registration

History-service takes over registration of received instances from instance-service. It is off
until `novari.flyt.instance-functionality.enabled=true`.

- `InstanceReceivedEventConsumerConfiguration` consumes `instance-received` with the fint-kafka
  listener factory, in its own consumer group (`<application-id>-instance-received`).
- `instance_received_offset` holds `next_offset` per topic and partition, and it decides where
  consumption starts. `InstanceReceivedPositions` seeks to `next_offset` on every assignment.
  - Once the consumer reads from `next_offset`, the next record it gets is processed, also when
    Kafka offsets have gaps, for example after transaction markers.
  - If the seek at assignment failed, the consumer starts at its committed Kafka offset. A record
    ahead of `next_offset` then moves the consumer back to `next_offset` first.
  - A record behind the position has already been processed and is ignored.
  - Without a readable row nothing is processed. The record is retried until the row exists.
  - `cluster_id` and `topic_id` are not used.
- For each record, `InstanceRegistrationService`:
  1. saves the instance graph
  2. publishes `instance-registered` and waits for the broker acknowledgement
  3. sets `next_offset = offset + 1`

  The position is written last, so it never gets ahead of processing. A retry of the same record
  reuses the saved instance and does not send again once the send has succeeded.
- A record without usable instance flow headers is logged and skipped without history, because
  history cannot be stored without headers.
- A missing payload or invalid input (a key over 255 characters, a null character, or a JSON `null`
  where text or an object is expected) is stored as one `INSTANCE_REGISTRATION_ERROR` together with
  the position, in one transaction.
- Every other failure is retried every 10 seconds without limit, and no record is skipped. The
  partition waits until the cause is fixed. A payload that is not valid JSON fails in the fint-kafka
  deserializer before it reaches the listener, and it also blocks the partition.
- The `INSTANCE_REGISTERED` history event comes from the ordinary `instance-registered` listener.
- Duplicates only occur when the process dies between the send and the position write. If it dies
  between the save and the send, the saved instance remains without an event.

### Instance schema

The instance tables stay in the instance-service schema in the same database. The JPA naming
strategy qualifies them explicitly, because `search_path` is unreliable behind PgBouncer. The
schema is derived from the database user: `<tenant>_fint_flyt_history_service_db` becomes
`<tenant>_fint_flyt_instance_service_db`. `novari.flyt.instance.database.schema` overrides this,
for local runs and tests. The database role needs `USAGE` on that schema, `SELECT`, `INSERT`,
`UPDATE` and `DELETE` on its tables, and access to its sequences.

### Operations

- Before the consumer is enabled, every partition needs a row. Set `next_offset` to the higher of
  the row written by instance-service and the committed offset of the instance-service consumer
  group, after instance-service has stopped. Check that the topic ID is unchanged and that
  `next_offset` lies within the earliest and end offsets of the partition. If neither the row nor
  the committed offset exists, stop and investigate.
- Only one service may consume `instance-received` at a time.
- If the topic is recreated, delete the rows and insert new ones before the service starts again.
  Otherwise an old `next_offset` that is still inside the new topic skips records.

## Deployment

- kustomize/base/ holds shared Flyt resources (Application, secrets, config maps, DB connection).
- kustomize/overlays/<org>/<env>/ contains per-organization/environment patches (namespace, Kafka topics, OAuth issuers, DB secrets).
- kustomize/templates/ stores overlay templates; regenerate overlays after edits with script/render-overlay.sh, which emits kustomization.yaml files in-place.
- Overlays still set `NOVARI_FLYT_INSTANCE_DATABASE_SCHEMA`, which overrides the derived instance schema with the same value.
- Validate overlays locally with `kustomize build kustomize/overlays/<org>/<env>`; deploying them is a separate step.

## Security

- Runs as an OAuth2 resource server (JWT) and only exposes internal APIs guarded by novari.flyt.web-resource-server.security.api.internal.
- AuthorizationService relies on UserAuthorizationService to authorize requested source-application candidates over OAuth2-protected HTTP before returning data or executing manual actions. Unfiltered lists first obtain distinct candidates from the event database.
- Manual event endpoints validate payloads, re-check authorization, ensure the latest status is ERROR, and return 404/400 for inconsistent histories.

## Observability & Operations

- Actuator liveness at `/actuator/health/liveness`, readiness at `/actuator/health/readiness`, and consumer monitoring at `/actuator/health/consumers`. Prometheus metrics are exposed at `/actuator/prometheus`.
- Custom statistics metrics are published for Grafana dashboards (see Metrics section below).
- Logs follow Spring defaults; Kafka consumers attach origin app IDs for traceability.
- Event table indexes (timestamp, composite source application keys) keep summary queries fast; Flyway migrations (src/main/resources/db/migration) manage schema evolution.

## Metrics

The service publishes the same statistics data from the HTTP API as Prometheus gauges for Grafana dashboards.

Metrics:

- `flyt_history_instance_count` — total counts by status (tags: `status`)
- `flyt_history_integration_count` — counts by source application, integration, and status (tags: `sourceapplication_id`, `integration_id`, `status`)
- `flyt_history_source_application_count` — counts by source application and status
  (tags: `org_id`, `sourceapplication_id`, `status`)

Status tag values: `total`, `in_progress`, `transferred`, `aborted`, `failed`.
When no integration data exists yet, `flyt_history_integration_count` is published with
`integration_id="__none__"` and zero values so dashboards can discover the metric immediately.
When no source-application data exists yet, `flyt_history_source_application_count` is published with
`sourceapplication_id="__none__"` and zero values so dashboards can discover the metric immediately.

Scrape endpoint: `/actuator/prometheus`

Refresh interval: `novari.flyt.history-service.metrics.refresh-ms` (default `60000`).

## Development Tips

- When adding a new EventCategory, decide whether Kafka should auto-create a listener (createKafkaListener=true) and update downstream categorization logic.
- Filter DTOs are validated via custom annotations (OnlyOneStatusFilter, OnlyOneTimeFilterType); extend validators when you add new filter types.
- Reuse ValidationErrorsFormattingService for consistent 422 payloads on new controllers.
- Request/reply topics keep only 5 minutes of history; use them for fresh data, not bulk exports.
- Use performanceTest task for heavier queries to avoid mixing them with the default test suite.

## Dependency Locking

The build uses [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html), so every build resolves exactly the same direct and transitive versions. The locked versions live in the committed `gradle.lockfile` files. A pull request therefore shows every transitive version change it causes. Gradle plugins are not locked, because Dependabot only updates `gradle.lockfile`; their versions are pinned in the `plugins` block instead.

**Automatic (Dependabot):** Dependabot updates the lockfiles in its own pull requests, so no manual step is needed. If two Dependabot pull requests conflict in a lockfile, Dependabot rebases the remaining one after the first is merged. If it does not, comment `@dependabot recreate` on the pull request.

**Manual:** Any change you make by hand that affects dependency resolution requires regenerating the lockfiles. That covers versions, `extra[...]` properties, constraints, and adding or removing a dependency:

```bash
./gradlew dependencies --write-locks
```

Commit the updated lockfiles together with the change. If you forget, the build fails both locally and in CI with an error saying the resolved version "is not part of the dependency lock state".

## Contributing

1. Create a feature branch.
2. Run ./gradlew test (and performanceTest if applicable) before opening a PR.
3. If you touch Kustomize overlays or templates, rerun script/render-overlay.sh and commit the generated changes.
4. Describe any new filters, Kafka topics, or manual actions in this README to keep operators aligned.

FINT Flyt History Service is maintained by the FINT Flyt team. Reach out via the internal Slack channel or open an issue in this repository with questions or enhancement ideas.
