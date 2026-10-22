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
next offset to consume. The table remains empty until instance-service starts writing offsets in
a later task. The existing instance tables remain in the instance-service schema; they are not
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

`InstanceRegistrationService.register(messageId, headers, instance)` is controlled by the master flag.
`InstanceReceivedMessageId` identifies the incoming record by topic, partition, and offset. Its topic
must not be blank, and partition and offset must be non-negative. The incoming consumer will supply
these values from the actual Kafka record when it is implemented; correlation IDs and source
instance IDs are not deduplication keys.

Registration first reserves the message identity in `processed_instance_received`, a technical table
created by Flyway V10 in the history schema. `InstanceReceivedMessageRepository` uses parameterized
`INSERT ... ON CONFLICT DO NOTHING` and requires an active transaction (`Propagation.MANDATORY`).
A competing insert waits for the transaction holding the same identity: after commit it reports a
duplicate, and after rollback it can reserve the identity and perform registration itself.

A new message saves its marker, the instance graph, and one `INSTANCE_REGISTERED` INFO event in the
same database transaction, using the existing transaction manager with REQUIRED propagation.
The event uses the generated instance ID, copied incoming headers, the injected clock, and this
service's configured `fint.application-id`. Incoming headers are unchanged. The internal result is
`InstanceRegistrationOutcome.Registered` containing the saved DTO, or `AlreadyProcessed` for a
committed duplicate. Duplicate processing does not read or recreate the instance graph or add
history, even if the graph has since been deleted. There is no registration overload without a
message identity; `InstanceService.save` remains the lower-level persistence operation.

A failure in any registration write, including flush or commit failure, rolls back the marker,
instance graph, and INFO history. An enclosing transaction can also roll back all three together.
Database exceptions propagate to the caller; this service does not record an error event itself.
The marker has no instance foreign key and is retained independently of instance cleanup. No
automatic marker expiration is implemented; any future retention must preserve the required replay
window. The existing `instance_received_offset` table remains separate and is not updated here.

The registration event timestamp represents registration performed here, using the injected clock.
It is not the timestamp of an incoming `instance-received` record. The existing Kafka history
listener reads the timestamp and origin application ID from the outgoing `instance-registered`
record, which is a different event from `instance-received`. The configured `fint.application-id`
identifies history-service as the service performing registration after the takeover; copying the
incoming record's origin would instead attribute registration to the producer of `instance-received`.

When reliable publication is implemented, determine the registration timestamp once and preserve
that same timestamp in both local history and the outgoing `instance-registered` record, including
publication retries. The outgoing origin application ID must match the application ID stored in
local registration history. This metadata must be persisted with the pending publication so it
survives restarts. This publication requirement is not implemented by the registration service yet.

The registration service has no Kafka dependencies and does not call the existing registered-event
producer. Consumers, offset handover, and reliable publication remain later work. When publication
is added, its durable pending record must be written inside the registration transaction for new
messages only. Deduplication alone does not guarantee delivery of `instance-registered`.
`InstanceRegistrationServiceIntegrationTest` verifies registration, duplicate delivery, independent
message identities, concurrent commit and rollback, redelivery after instance deletion, retry after
database failures, and rollback of an enclosing transaction against PostgreSQL.

`InstanceRegistrationHandler.handle(messageId, headers, instance)` is controlled by the same master
flag. It requires no active transaction (`Propagation.NEVER`), allowing `InstanceRegistrationService`
to complete or roll back its own transaction before an exception is caught, including a commit
failure. It returns the registration outcome without adding history for `AlreadyProcessed`.
On registration failure, it records one `INSTANCE_REGISTRATION_ERROR` through the existing
`InstanceErrorEventService` in a separate, subsequent transaction. The error uses the original
incoming headers, the injected clock, this service's `fint.application-id`, and
`FINT_FLYT_INSTANCE_SERVICE_GENERAL_SYSTEM_ERROR` without arguments. A generated instance ID from
a rolled-back registration is never attached. The error service retains REQUIRED propagation;
it starts a transaction because the handler has none. Calls within an active transaction are
rejected before registration or error recording.

The handler always rethrows the original registration exception, even after error history commits.
If error recording also fails, that exception is added as suppressed on the original exception.
Persisting error history does not mark the incoming record as processed or authorize offset advancement.
The handler has no Kafka dependencies, publishes no error event, and changes no listeners or offsets.
Failed attempts are not deduplicated: another failed attempt can record another ERROR event, while a
later successful retry can register the same message identity. The registration tests retain coverage
of INFO commit failure followed by a committed ERROR, failure of both transactions, and rejection
of an outer transaction.

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
