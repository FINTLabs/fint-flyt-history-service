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

C4 diagrams (level 1 to 3) are in [docs/architecture.md](docs/architecture.md).

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

## Instance retries

When `novari.flyt.instance-functionality.enabled=true`, history-service exposes the existing internal retry API:

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/api/intern/handlinger/instanser/{instanceId}/prov-igjen` | None | 200 after send returns; 404 missing instance; 500 missing headers or synchronous failure. |
| POST | `/api/intern/handlinger/instanser/prov-igjen/batch` | Array of IDs | 200 after attempting each ID; 500 if interrupted. |

Each attempt loads the stored graph and latest registration headers locally and generates a new correlation ID.
HTTP 200 means the Kafka send call returned successfully; delivery and processing are asynchronous.
Synchronous publication failures are stored locally as `INSTANCE_RETRY_REQUEST_ERROR`, and batch processing continues
with later IDs. Batches have no application-level size limit and stop with HTTP 500 if the thread is interrupted.

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
`InstanceCleanupService` runs 30 seconds after startup and then every 24 hours when the instance functionality flag
is enabled. It deletes root instances older than the shared `novari.flyt.history-service.retention.time-to-keep-in-days`.
Both jobs default to 60 days, using event timestamps for scrubbing and instance creation timestamps for cleanup.

## Configuration

The service layers Spring profiles (flyt-kafka, flyt-logging, flyt-web-resource-server,
flyt-authorization-client, flyt-postgres) and exposes these key properties:

| Property                                                                                   | Description                                                                                            |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| fint.application-id                                                                        | Identifier included in events created by this service (default fint-flyt-history-service).             |
| novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time          | Retention for instance-processing event topics (default 4d).                                           |
| novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled                     | Enables listeners for the five legacy error topics (default true).                                     |
| novari.flyt.history-service.retention.time-to-keep-in-days                                 | Days to keep stored instances and error details before scheduled cleanup.                              |
| novari.flyt.history-service.retention.scrub-batch-size                                     | Maximum number of events scrubbed per database transaction.                                            |
| novari.flyt.instance-functionality.enabled                                                 | Enables instance registration, retry and cleanup (default true). Deletion after dispatch always runs. |
| novari.flyt.instance.database.schema                                                       | Instance schema override; derived from the database user when empty.                                   |
| fint.database.url, fint.database.username, fint.database.password                          | PostgreSQL JDBC connection supplied via secrets/environment.                                           |
| spring.kafka.bootstrap-servers                                                             | Kafka cluster endpoint; application-local-staging.yaml defaults to localhost:9092.                     |
| spring.security.oauth2.resourceserver.jwt.issuer-uri                                       | Authority used for JWT validation.                                                                     |
| fint.flyt.authorization.sso.client-id / client-secret                                      | OAuth2 client credentials for authorization-service.                                                   |
| no.novari.flyt.web-resource-server.security.api.internal.authorized-org-id-role-pairs-json | Mapping of org IDs to roles that may call the internal API.                                            |
| server.max-http-request-header-size                                                        | Raised to 40KB to handle large JWTs.                                                                   |
| spring.jackson.time-zone                                                                   | Forces JSON serialization to UTC.                                                                      |

Secrets referenced in the base Kustomize manifests must include DB credentials and OAuth client metadata.

## Running Locally

Prerequisites: Java 25+, Gradle wrapper (bundled), and Docker.

1. Start Postgres on localhost:5436 and Kafka on localhost:9092: `docker compose up -d`
2. Export SPRING_PROFILES_ACTIVE=local-staging to pick up application-local-staging.yaml.
   The profile sets the instance schema to `fintlabs_no_instance`, which Docker creates empty. With the
   functionality flag enabled by default, startup runs its Flyway migrations before JPA. If the flag is
   explicitly disabled, dispatched deletion still requires existing instance tables.

Add `--profile tools` to also start Kafdrop on http://localhost:19000. Kafka topics and the database are empty on every start.

Useful commands:

./gradlew clean build                  # compile + unit tests
./gradlew bootRun                      # run Spring MVC app with local profiles
./gradlew test                         # run default test suite
./gradlew performanceTest              # run tests tagged @Tag("performance")

## Instance registration

History-service takes over registration of received instances from instance-service.
`novari.flyt.instance-functionality.enabled` defaults to `true`; this version must only be deployed
after instance-service has been removed and the cutover positions have been prepared.

- `InstanceReceivedEventConsumerConfiguration` owns the `instance-received` topic configuration
  (one partition, configured processing-event retention, normal cleanup), and consumes it with the fint-kafka
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
- Every other failure in the listener is retried every 10 seconds without limit, and no record is
  skipped. The partition waits until the cause is fixed.
- A payload that is not valid JSON fails in the fint-kafka deserializer during `poll`, before it
  reaches the listener. Spring Kafka cannot assign it to a record, so the error handler's 10-second
  back-off does not apply: the container polls again at once and logs the error on every attempt.
  The partition is blocked until the record is skipped by hand (see Operations). Instance-service
  has the same limitation.
- The `INSTANCE_REGISTERED` history event comes from the ordinary `instance-registered` listener.
- Process death between saving, sending and writing the position can leave an instance without
  an event and create another instance on replay. Death after sending, an ambiguous acknowledgement
  failure, or takeover by another process can also duplicate publications. In-memory retry state
  does not provide a durable exactly-once guarantee.

### Instance schema

The instance tables stay in the instance-service schema in the same database. The JPA naming
strategy qualifies them explicitly, because `search_path` is unreliable behind PgBouncer. The
schema is derived from the database user: `<tenant>_fint_flyt_history_service_db` becomes
`<tenant>_fint_flyt_instance_service_db`. `novari.flyt.instance.database.schema` overrides this,
for local runs and tests. The database role needs `USAGE` on that schema, `SELECT`, `INSERT`,
`UPDATE` and `DELETE` on its tables, and access to its sequences. Checkpoint SQL qualifies the
history schema from `spring.datasource.hikari.schema`; other history persistence still uses the
configured default schema.

Flyway runs both migration histories before JPA starts, using the same datasource:

- `db/migration` manages the history schema as before.
- `db/instance-migration` manages the existing instance schema and its own `flyway_schema_history`.
  V1–V4 are copied unchanged from instance-service. They are validated, not replayed when already applied.

The instance schema must exist. There is no automatic baseline, repair or ownership transfer.
Keep both history tables, the instance tables, their IDs and sequences. The history-service role
also needs access to the existing instance Flyway history and ownership rights for future DDL
(for example inherited membership of the existing owner role, or a DBA-managed ownership transfer).
`GRANT ALL` on tables alone does not permit `ALTER TABLE`. Arrange this before deploying; retain
the old database owner role if ownership is inherited from it.

### Operations

- Deploy instance-service #90 first, then history-service #142, before this takeover version.
  Schema access, including `DELETE`, and Kafka rights for the `instance-deleted` producer must
  already be in place: dispatched deletion runs even with the functionality flag off.
- This version enables registration, retry and cleanup by default and adds the existing retry
  routes in all overlays. Limit deployment to the organisation and environment being migrated;
  the CD workflow currently lists all 30 targets. Other targets must keep their existing version
  and routing until their cutover.
- Before stopping instance-service, complete the #142 rollout and verify that instance-service's
  dispatched consumer has no lag on the organisation's partitions. The history group may previously have
  recorded those events without deleting instances, so let instance-service drain that backlog.
- Stop instance-service through Git/GitOps and wait for its pod and in-flight work to finish.
  Preserve its database schema, data, sequences, Flyway history and required owner role. Remove
  the old deployment and retry ingress before deploying this version.
- After instance-service has stopped, manually read and save its final committed offsets from
  Aiven: group `fint-flyt-instance-service`, topic `<org>.flyt.event.instance-received`, separately
  for each partition. Use the consumer group's committed/current offset, not the topic's log-end
  offset. Committed offsets already identify the next record; do not add 1.
- Every received partition needs an `instance_received_offset` row before activation:
  - If the row is missing, insert it with the committed offset read from Aiven.
  - If both exist, use the higher of the database position and the committed offset; update the
    row when it is behind.
  - If only the database row exists, use that position after the checks below.
  - If both are missing, stop and investigate. Initialize a new, unused partition only after
    verifying its start position. An empty topic alone does not prove it never had records.
- Before writing a position, verify the cluster ID and topic ID against the recorded reference,
  and check `earliestOffset <= next_offset <= endOffset`. Read back every inserted or updated row.
  There is no requirement to wait through a retention period or for new traffic to create rows.
- No `instance-dispatched` offsets are transferred. Deletion continues in the existing history
  group's listener; the separate `<application-id>-instance-dispatched` group has been removed.
- Consumer groups remain shared across organisations on the same cluster. Each listener subscribes
  to its organisation's topic. Stopping one organisation does not empty the whole group. Any manual
  Kafka offset reset must be scoped to the relevant topic partitions and coordinated with other
  members, because the target group must be inactive.
- Before activation, verify Flyway/DDL rights on the instance schema and Kafka rights to create or
  modify `instance-received`. Keep the schema overrides until actual database usernames are verified.
- Only one service may own received registration and scheduled instance cleanup per organisation.
  After deploying, verify both ingress routes, authorization, registration, history, retry, deletion
  and checkpoint progress. Readiness alone does not prove progress.

#### Rollback

Stop and drain history-service for the affected organisation, including cleanup and dispatched
handling. The functionality flag alone does not stop dispatched deletion. Preserve the schemas
and both Flyway histories. Capture the received position from the database, validate identity and
bounds, and transfer it to the old instance-service group's received partitions without adding 1.
Coordinate any reset with other members of the shared group. Do not reset the history group's
dispatched offsets or copy them into a separate group. Check that the old dispatched commits
are still within retention; its #90 deletion logic can skip instances already deleted by history-service.
Restore the old retry ingress and the patched instance-service deployment through Git. History-service
can return to #142 with the functionality flag off. Do not restart instance-service with stale
received offsets. Already published events and deleted data are not reversed by offset changes;
investigate these before deciding whether rollback is appropriate.

#### Malformed records and topic recreation

- To skip a record that cannot be deserialized:
  1. Stop history-service for the affected organisation and wait for its work to finish. Changing
     `next_offset` while it runs has no effect, because the position is read at partition assignment.
  2. Find the offset in the log and inspect the record.
  3. If it is to be skipped, set `next_offset` to that offset + 1 for the partition.
  4. The `INSTANCE_RECEIVED` history listener reads the same topic in the default group. If it is
     blocked by the same record, reset only that topic partition in the default group to the same
     value. Coordinate inactivity of the shared group before `kafka-consumer-groups --reset-offsets`.
  5. Start history-service again.
- If the topic is recreated, delete the rows and insert new ones before the service starts again.
  Otherwise an old `next_offset` that is still inside the new topic skips records.

## Instance deletion and cleanup

Deletion after `instance-dispatched` runs regardless of the instance functionality flag. The ordinary history
listener for `instance-dispatched` records the event and then deletes the instance, in the default group, so no
offsets need to be carried over from instance-service at cutover. The flag enables scheduled cleanup of old root
instances.
Deletion locks the root row, commits the complete instance graph and then publishes `instance-deleted`
asynchronously with the original flow headers, no key and a null payload. The ordinary history listener records
the event.

Until cutover, instance-service deletes dispatched instances too. Whichever service deletes the row publishes
the event. The other waits for the row lock, finds nothing and publishes nothing.

Dispatch handling uses `noRetries().skipFailedRecords()`, matching instance-service. A failed delete leaves
an instance for the scheduled cleanup job, so the partition can continue. If deletion succeeds but publishing
fails, the deletion event can be lost; cleanup cannot recreate it after the instance has been removed.

Cleanup uses the latest registration headers from local history. If they are missing, it deletes the instance
and logs a warning without publishing an event. A failed candidate does not stop later candidates.
Both instance cleanup and error-detail scrubbing use the shared retention setting, which defaults to 60 days.

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
