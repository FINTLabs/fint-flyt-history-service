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
| novari.flyt.instance.database.schema                                                     | Existing instance schema, configured through NOVARI_FLYT_INSTANCE_DATABASE_SCHEMA.        |
| fint.database.url, fint.database.username, fint.database.password                          | PostgreSQL JDBC connection supplied via secrets/environment.                               |
| spring.kafka.bootstrap-servers                                                             | Kafka cluster endpoint; application-local-staging.yaml defaults to localhost:9092.         |
| spring.security.oauth2.resourceserver.jwt.issuer-uri                                       | Authority used for JWT validation.                                                         |
| fint.flyt.authorization.sso.client-id / client-secret                                       | OAuth2 client credentials for authorization-service.                                      |
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

Flyway creates the local `fintlabs_no` history schema and the `instance_received_offset` table
inside it when history-service starts. Each row identifies a topic and partition and stores the
next offset to consume. V10 adds the Kafka cluster ID and topic ID to this existing table. Production
cutover and the legacy writer remain later work. The explicit position service described below
can validate and initialize positions; registration refuses missing positions or unverified topic
generations. The existing instance tables remain in the instance-service schema; they are not
copied into the history schema. In deployed environments both schemas belong to the same
`fint-flyt` database. The separate local Postgres containers do not represent this topology.

`novari.flyt.instance.database.schema` explicitly selects the existing instance schema. The JPA
naming strategy resolves the logical `flyt_instance` schema on the instance entities and their
collection table to this configured name, without adding it to `search_path`. History entities
continue to use the existing history schema and naming rules.

`novari.flyt.instance-functionality.enabled` defaults to `false`. When it is missing or `false`,
only history entities and repositories are registered, and no instance schema configuration is
required. When explicitly set to `true`, `InstanceJpaConfiguration` registers both entity domains,
`EventRepository`, and `InstanceRepository` against the existing `entityManagerFactory` and
`transactionManager`. Both domains share one datasource, EntityManagerFactory, and transaction
manager. A missing or blank instance schema then fails startup with a configuration error.
The instance repository provides CRUD operations and `findAllOlderThan` with a strict timestamp
threshold. Registering it does not introduce instance processing, retention jobs, or Kafka handover.
The default flag and deployment overlays remain unchanged; enabling instance functionality in a
deployed environment is a separate cutover step.

The same flag also controls `InstanceMappingService` and `InstanceService`. The mapper recursively
copies the DTO graph and converts entity creation timestamps from `Instant` to `Date`. Incoming
DTO IDs and creation timestamps are ignored when creating entities; saving an existing DTO again
creates a new graph rather than updating the original. The DTO contract is unchanged.

`InstanceService.save` maps, persists, and maps the result back within one write transaction.
`getById` and `getAllOlderThan` map the complete graph within read-only transactions so returned
DTOs can be used after the transaction ends without lazy loading. Missing IDs propagate
`EntityNotFoundException`. The age query uses the existing injected `Clock` and a strict
`createdAt < now - days` threshold and returns only root instances. Nested objects remain part
of each returned DTO graph rather than separate query results. These services use the shared
transaction manager and have no Kafka dependencies. Deletion flows, cleanup jobs, controllers,
and event publication remain later work.

`InstanceRegistrationService.register(messageId, expectedNextOffset, headers, instance)` is controlled
by the master flag. `InstanceReceivedMessageId` contains the topic, partition, offset, Kafka cluster
ID, and topic ID. The incoming consumer must obtain the real cluster and topic IDs from broker
metadata and pass the actual record position; correlation IDs and source instance IDs are not
deduplication keys. Topic IDs are represented as UUIDs; the Kafka UUID's most and least significant
bits can be used when adapting broker metadata. Blank topic or cluster IDs, negative positions,
unknown (zero) topic IDs, and offsets that cannot be incremented are rejected.

`InstanceReceivedOffsetRepository` requires an active transaction (`Propagation.MANDATORY`) and
locks the existing `instance_received_offset` row with `SELECT ... FOR UPDATE`. It verifies the
stored cluster and topic IDs before checking for duplicates, including when the incoming offset
is below `next_offset`. A lower offset returns `InstanceRegistrationOutcome.AlreadyProcessed` and
writes an INFO log containing cluster, topic ID, topic, partition, record offset, and stored position.
It does not load or recreate an instance graph or add history, even after instance deletion.

A new record must supply the expected preceding position. If this differs from the stored
`next_offset`, processing fails without advancing the position. After instance and INFO history
are saved, an outbox snapshot is inserted and a conditional update sets `next_offset` to the record
offset plus one, matching the locked position and generation. Position, instance graph, INFO
history, and outbox snapshot commit or roll back together with the existing transaction manager
and REQUIRED propagation. An enclosing transaction can also roll back all four. The result for
a new record is `InstanceRegistrationOutcome.Registered`
containing the saved DTO. There is no registration overload bypassing the position checks;
`InstanceService.save` remains the lower-level persistence operation.

The consumer must process records sequentially per partition, keep its expected position aligned
with successful processing, and stop that partition on any unresolved failure. It must not dispatch
unordered work or continue to later records after a failure. A row lock does not itself establish
Kafka record order. Kafka offsets may contain gaps, so a record above the persisted position is
accepted when its expected preceding position matches; the database cannot independently prove
that such a gap contains no unprocessed records. Parallel processing of different partitions is
allowed. Ownership changes must restore the database position before processing starts.

There is one row per topic and partition, retained independently of instance cleanup. V10 no longer
creates a per-message marker table. Both generation columns are nullable only to preserve existing
V9 rows without inventing broker metadata; registration refuses unverified rows. The controlled
handover must initialize or bind each position using verified Kafka identity and the agreed next
offset, with one active writer. Registration never guesses an initial offset or automatically binds
a legacy row. A changed cluster or topic ID raises `InstanceReceivedOffsetException` and requires
an explicit controlled transition; it never silently resets, adopts the new generation, or skips
new records using the old generation's progress. This applies to topic recreation and cluster
migration. Consumer-position rewind within the same topic generation remains ordinary redelivery.

The registration event timestamp represents registration performed here, using the injected clock.
It is not the timestamp of an incoming `instance-received` record. The existing Kafka history
listener reads the timestamp and origin application ID from the outgoing `instance-registered`
record, which is a different event from `instance-received`. The configured `fint.application-id`
identifies history-service as the service performing registration after the takeover; copying the
incoming record's origin would instead attribute registration to the producer of `instance-received`.

Registration determines its timestamp once from the injected clock, truncated to milliseconds.
Both local INFO history and the outbox snapshot use that timestamp and the configured application
ID. Kafka publication uses the stored values on every attempt, including after restarts. The
registered-event producer verifies `CreateTime` once at startup, after topic creation. A topic
using `LogAppendTime` prevents startup when the feature is enabled. Publication performs no topic
configuration lookup or acknowledged-timestamp comparison per record. The dedicated Admin client
is closed after the startup check, including when the check fails.

The registration service has no direct Kafka publication. V11 creates `instance_registered_outbox`
in the history schema, with a unique source message identity and no foreign key to the instance
graph. Headers and the complete saved DTO graph are stored as JSON snapshots. An internal snapshot
model preserves generated IDs when reading the queue while the public DTO retains its existing
read-only ID deserialization contract. Publication never reloads the instance graph.

The incoming consumer, its metadata refresh lifecycle, and operational offset handover remain
later work. Metadata lookup and explicit position initialization are provided below. The outbox
does not activate that consumer or initialize processing positions. Kafka and PostgreSQL
integration tests cover atomic registration, queue reservations, retries, restart recovery, broker
metadata, and existing registration rollback behavior.

`InstanceRegistrationHandler.handle(messageId, expectedNextOffset, headers, instance)` is controlled
by the same master flag. It requires no active transaction (`Propagation.NEVER`), allowing the
registration transaction to finish before exceptions are caught, including commit failures.
An invalid expected or persisted position, including a negative `expectedNextOffset`, or a topic
generation mismatch raises `InstanceReceivedOffsetException`. It is logged as an ERROR and rethrown
without creating business error history or advancing the position. The future consumer must stop
the affected partition and require resolution instead of using its existing skip-failed-records policy.

Other registration failures still record one `INSTANCE_REGISTRATION_ERROR` through the existing
`InstanceErrorEventService` in a separate, subsequent transaction. This uses the original incoming
headers, the injected clock, this service's `fint.application-id`, and
`FINT_FLYT_INSTANCE_SERVICE_GENERAL_SYSTEM_ERROR` without arguments. A generated instance ID from
a rolled-back registration is never attached. The error service retains REQUIRED propagation;
it starts a transaction because the handler has none. The original exception is always rethrown;
if error recording also fails, that exception is added as suppressed on the original exception.
Successful error recording does not advance the processing position. Repeated failed attempts may
still create repeated ERROR history. Calls with an active outer transaction remain rejected.

### Instance-received identity and position handover

`InstanceReceivedKafkaMetadataService.read()` resolves the `instance-received` topic through
`TopicNameService.validateAndMapToTopicName`, using the same application-default organization and
domain prefix as `EventListenerConfiguration`. It reads the cluster ID with
`describeCluster().clusterId()`, the topic ID, partitions, and earliest/end offsets. The cluster ID
identifies the Kafka deployment; it is not the numeric ID of an individual broker. Topic IDs are
converted from Kafka `Uuid` using their two 64-bit values, without parsing the base64 string.

Metadata reads use a qualified, non-default `Supplier<Admin>` built from the shared
`ProducerConfig` connection and security settings. A fresh client is closed with `use` after each
lookup, including failures. `novari.flyt.instance.kafka.metadata-timeout` defaults to `10s` and
bounds each request/future wait independently of the outbox send timeout. The offset requests use
the isolation level from the actual shared `ConsumerConfig`. For `read_committed`, the upper
bound is the last stable offset (LSO), so an open Kafka transaction cannot extend the accepted
range beyond what the consumer can read. The future consumer must use the same isolation level;
listener-specific overrides must also be reflected in this metadata lookup.

`InstanceReceivedPositionService.getValidatedPosition(partition)` fetches current metadata and
checks the stored position. Missing rows, unbound V9 rows, different cluster/topic IDs, absent
partitions, and offsets outside the inclusive `[earliest, end]` range are technical
`InstanceReceivedOffsetException`s. A position equal to `end` is valid and means caught up. Nothing
is initialized, reset, or corrected as a side effect. No registration ERROR history is created.

`InstanceReceivedPositionService.initialize(request)` is the internal entry point for the later
controlled one-off handover job. This task adds no automatic startup call, admin endpoint, job
runner, consumer, or environment activation. Each request explicitly supplies the resolved topic,
partition, cluster ID, topic ID, next offset, and expected preceding database state:

- `ExpectedInstanceReceivedPosition.Missing` allows inserting a missing row. `INSERT ... ON CONFLICT
  DO NOTHING` handles concurrent creation; a conflict is read and compared instead of overwritten.
- `ExpectedInstanceReceivedPosition.Legacy(nextOffset)` binds cluster/topic identity to an existing
  V9 row only when its next offset matches the explicit expectation. Binding preserves the offset.
- An already identical initialization is a no-op. Other existing states, including later progress
  or another generation, are rejected. No generic reset/rewind operation is exposed.

Both public position operations reject an outer database transaction. Kafka is read before the
short database transaction; binding uses a row lock and a conditional update. The existing
registration path continues to check and lock positions in its own transaction.

The planned legacy writer in instance-service must persist verified completed progress in
`instance_received_offset`; implementing that writer and its permissions remains cutover work.
Those identity-less V9 rows form the normal handover basis: history-service validates their
explicit next offsets and assigns the actual Kafka identity. A missing row requires a separately
verified explicit basis. Kafka consumer group commits are not adopted automatically. If an
approved handover basis uses a committed Kafka offset, it is already the next offset and must not
be incremented again.

Stop the old writer and drain in-flight work before capturing the final basis. Initialize or bind
all required partitions before starting the new consumer, with one active processing owner.
Metadata and retention boundaries are snapshots, not an atomic transaction with Kafka: retention
or topic recreation can occur after validation. The future consumer must refresh identity on
assignment, restore the validated database position, use `auto.offset.reset=none`, and stop on
`OffsetOutOfRange` or identity changes instead of falling back to earliest/latest. That consumer
behavior and the operational stop/start procedure are implemented in later tasks. No existing
listener settings or consumer group offsets are changed here.

### Instance registration outbox

`InstanceRegisteredOutboxPublisher` is protected by the existing master flag and runs on its own
scheduler. Each bounded run reserves eligible work in short database transactions using row locks
and `SKIP LOCKED`, then waits for Kafka outside a database transaction. A fresh UUID token fences
completion and failure updates; an expired reservation can be taken over after a crash. Reservation
and retry times use the database clock rather than pod clocks.

Eligible outbox rows can be published independently, including rows from the same source partition.
A leased row or a row waiting for retry does not block other eligible rows. Publication does not
guarantee the order of incoming records; the incoming consumer must still process its records
sequentially per partition. Confirmed rows are deleted only after the broker acknowledges
publication. Failed or unconfirmed rows remain with capped exponential backoff and a technical
error type; there is no attempt limit that discards data. A stale owner cannot delete or change
a row claimed by another publisher. If deletion fails after Kafka acknowledgement, the row remains
reserved until its lease expires and can then be delivered again. This database cleanup failure
is logged separately and does not increment the publication failure counter.

Delivery is at least once. If Kafka accepts a record but the acknowledgement is lost, or the
process stops before the queue deletion commits, the same event can be published again. A lease
expiry can also produce duplicate sends. Stored headers, instance IDs, payload, timestamp, and
origin remain unchanged, but readers must tolerate repeated events; producer idempotence alone
does not deduplicate application-level resends across restarts. Confirm reader behavior before
activation. Controlled topic-generation transitions must account for outstanding queue entries.

The dedicated producer inherits the existing connection and serialization configuration, uses
`acks=all` and idempotence, and retains the existing null record key and topic naming. Its own
origin interceptor is excluded so the stored origin header is not replaced by the current runtime
application ID. The adapter uses the shared instance-flow header mapper and DTO JSON serializer.
Kafka send failures also reach the existing `ProducerFailureTracker` when available. The forwarded
record contains only the topic, and the forwarded exception contains only its technical type,
without the original message, payload, or cause.

Its Kafka clients and scheduler are qualified, non-default beans so other Kafka components and
scheduled jobs keep their existing configuration. Configure `message.timestamp.type=CreateTime`
on the topic or broker before activation. The startup check validates this prerequisite without
changing topic settings.

Configuration under `novari.flyt.instance.outbox`:

| Property              | Default | Purpose                                                                                                                                          |
| --------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `poll-delay`          | `1s`    | Delay between completed runs.                                                                                                                    |
| `max-records-per-run` | `100`   | Maximum attempts during one run.                                                                                                                 |
| `send-timeout`        | `30s`   | Timeout for the startup topic check and each acknowledgement wait. Producer metadata/buffer blocking is bounded separately by the same duration. |
| `claim-duration`      | `2m`    | Reservation lifetime; must exceed twice `send-timeout`.                                                                                          |
| `retry-initial-delay` | `5s`    | Delay after the first unresolved attempt.                                                                                                        |
| `retry-max-delay`     | `5m`    | Maximum exponential retry delay.                                                                                                                 |

Metrics are `flyt.instance.outbox.pending`, `flyt.instance.outbox.oldest.age.seconds`, and
`flyt.instance.outbox.publish.failures`. Queue gauges refresh on each run; the failure counter is
per running process and counts snapshot decoding or Kafka publication failures. Each pod reports
the full queue gauges for its database. Aggregate gauges with `max` across replicas of the same
organization and environment; summing them would count the same queue multiple times. The failure
counters are local to each pod and can be summed across those replicas. Application logs contain
technical IDs and error types, without snapshots or complete headers. Published rows and their
payloads are removed; unresolved rows are retained for
recovery and must be monitored. Serialization failures remain pending while other eligible rows
continue. Repeated publication failures do not create business registration errors.

Keep the master flag disabled in deployments until the complete processing flow is ready:
enabling it already disables the Kafka history listener for `INSTANCE_REGISTERED`, while the
registration service currently has no incoming consumer calling it.

Flyway continues to manage only the history schema. Instance tables, sequences, and existing
Flyway history remain in their original schema. Taking over instance migrations is later work.
`InstanceSchemaMappingIntegrationTest` enables the production JPA configuration with the master
flag and uses two schemas in one PostgreSQL container, a fixture matching instance-service V1–V4,
and `ddl-auto=validate`. It verifies repository CRUD, cascade and orphan removal, the strict
age threshold, history native queries, and rollback across both repositories. Context tests also
verify history-only startup with a missing or false flag and startup failure when instance
functionality is enabled without a valid schema. `InstanceServiceIntegrationTest` calls the
Spring-managed service without an enclosing test transaction, with lazy loading outside
transactions disabled. It covers detached DTO graphs, new identities on repeated saves,
clock-based age filtering, unknown IDs, and complete rollback on a nested database error.

Flyway migrations run at startup. Before offset handover is enabled, the instance-service database
user must have `USAGE` on the history schema and `SELECT`, `INSERT`, and `UPDATE` on its
`instance_received_offset` table. The required role names and grants must be verified for each
environment. Before instance functionality is activated, the history-service database role also
needs `USAGE` on the existing instance schema, the required table privileges (`SELECT`, `INSERT`,
`UPDATE`, and `DELETE` for the future processing flow), and access to its existing ID sequences.
Task 3 must verify actual roles and grants in each environment; passing a container test does not
confirm deployed permissions.

## Deployment

- kustomize/base/ holds shared Flyt resources (Application, secrets, config maps, DB connection).
- kustomize/overlays/<org>/<env>/ contains per-organization/environment patches (namespace, Kafka topics, OAuth issuers, DB secrets).
- kustomize/templates/ stores overlay templates; regenerate overlays after edits with script/render-overlay.sh, which emits kustomization.yaml files in-place.
- Each history-service overlay explicitly sets `NOVARI_FLYT_INSTANCE_DATABASE_SCHEMA` on the Application resource. For example, AFK uses `afk_no_fint_flyt_instance_service_db` and VLFK uses `vlfk_no_fint_flyt_instance_service_db`, in both api and beta. The render script derives these manifest values from the organization ID, never from the history-service database username. This configuration does not activate instance functionality.
- Validate overlays locally with `kustomize build kustomize/overlays/<org>/<env>`; deploying them is a separate step.

## Security

- Runs as an OAuth2 resource server (JWT) and only exposes internal APIs guarded by novari.flyt.web-resource-server.security.api.internal.
- AuthorizationService relies on UserAuthorizationService to authorize requested source-application candidates over OAuth2-protected HTTP before returning data or executing manual actions. Unfiltered lists first obtain distinct candidates from the event database.
- Manual event endpoints validate payloads, re-check authorization, ensure the latest status is ERROR, and return 404/400 for inconsistent histories.

## Observability & Operations

- Actuator readiness at /actuator/health and Prometheus metrics at /actuator/prometheus (Micrometer Prometheus registry enabled).
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

## Contributing

1. Create a feature branch.
2. Run ./gradlew test (and performanceTest if applicable) before opening a PR.
3. If you touch Kustomize overlays or templates, rerun script/render-overlay.sh and commit the generated changes.
4. Describe any new filters, Kafka topics, or manual actions in this README to keep operators aligned.

FINT Flyt History Service is maintained by the FINT Flyt team. Reach out via the internal Slack channel or open an issue in this repository with questions or enhancement ideas.
