# Architecture

C4 level 1 to 3 for fint-flyt-history-service. Blue is this service, purple is the database,
turquoise is external.

## C4 level 1: System context

Every FLYT service publishes what happens to an instance as events on Kafka. History-service
stores all of them and is the place users go to see where an instance is and to fix failed ones.

```mermaid
C4Context
    title System context for fint-flyt-history-service

    System_Ext(frontend, "fint-flyt-frontend", "Users follow instances and handle errors.")
    System_Ext(gateways, "FLYT gateways", "Receive instances from source applications.")
    System(history, "fint-flyt-history-service", "Stores the history of every instance and serves status and statistics.")
    System_Ext(services, "Other FLYT services", "instance-service, mapping-service, archive-gateway.")
    System_Ext(authz, "authorization-service", "fint-flyt-authorization-service. Which source applications a user has access to.")
    System_Ext(idp, "NAM", "Issues OAuth2 tokens.")

    Rel(frontend, history, "Status, manual actions", "HTTPS")
    BiRel(gateways, history, "Instances, archive IDs", "Kafka")
    BiRel(history, services, "Instance events", "Kafka")
    Rel(history, authz, "User access", "HTTP")
    Rel(history, idp, "Fetches token", "client_credentials")

    UpdateRelStyle(frontend, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(gateways, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(history, services, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(history, authz, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(history, idp, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(history, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(frontend, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(gateways, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(services, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(authz, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(idp, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- **Gateways** publish `instance-received` and `instance-receival-error`, and ask for the archive
  instance ID of an earlier instance over request/reply (`archive-instance-id`).
- **Other FLYT services** publish the later events in the flow (`instance-registered`,
  `instance-mapped`, `instance-dispatched`, `instance-error`, `instance-deleted` and more).
  Instance-service asks for the instance flow headers of a registered instance over request/reply.
- **Instance registration** is moving from instance-service to history-service. When
  `novari.flyt.instance-functionality.enabled=true`, history-service consumes `instance-received`,
  stores the instance and publishes `instance-registered`, which mapping-service consumes. It is
  off by default. The same flag enables deletion after `instance-dispatched` and daily instance cleanup.
- **Users** call the API from fint-flyt-frontend with a JWT. History-service asks
  authorization-service which source applications the user has access to, with a
  client_credentials token from NAM.

## C4 level 2: Containers

One Spring Boot application and one PostgreSQL database.

```mermaid
C4Container
    title Containers

    System_Ext(frontend, "fint-flyt-frontend", "Browser app.")
    ContainerQueue_Ext(kafka, "Kafka", "Topics", "Instance flow events, request/reply, instance-received, instance-registered, instance-dispatched, instance-deleted")
    Container(app, "fint-flyt-history-service", "Kotlin, Spring Boot 3, Java 25", "Consumes events, serves the internal API, registers and deletes instances.")
    System_Ext(prometheus, "Prometheus", "Metrics in the cluster.")
    ContainerDb(db, "PostgreSQL", "Flyway, JPA", "History schema: events, errors, next_offset. Instance-service schema: instances.")
    Container_Ext(authz, "authorization-service", "Spring Boot", "fint-flyt-authorization-service. User access to source applications.")

    Rel(frontend, app, "API", "HTTPS, JWT")
    BiRel(app, kafka, "Events, replies", "flyt-kafka")
    Rel(prometheus, app, "Scrapes", "HTTP")
    Rel(app, db, "Reads, writes", "JDBC")
    Rel(app, authz, "User access", "HTTP")

    UpdateRelStyle(frontend, app, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(prometheus, app, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(app, db, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, authz, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(app, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(db, $bgColor="#C4B5FD", $fontColor="#1F2937", $borderColor="#8B5CF6")
    UpdateElementStyle(frontend, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(prometheus, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(authz, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- The ingress only routes `/api/intern/instance-flow-tracking`. Swagger UI, OpenAPI and Actuator
  are only reachable inside the cluster.
- The instance tables stay in the instance-service schema in the same database. See
  [Instance schema](../README.md#instance-schema).
- The token for authorization-service comes from NAM (not shown).

## C4 level 3: Components

The components below share the existing database and Kafka infrastructure.

### HTTP API

```mermaid
C4Component
    title HTTP API

    System_Ext(frontend, "fint-flyt-frontend", "Browser app.")
    Container_Ext(authz, "authorization-service", "Spring Boot", "fint-flyt-authorization-service. User access to source applications.")
    Component(controller, "HistoryController", "Spring MVC", "Summaries, events, statistics, manual actions. ValueSpaceController serves the enum lists.")
    Component(authService, "AuthorizationService", "Service", "Filters source applications to those the user may see.")
    Component(manual, "Manual events", "Service", "ManualEventCreationService. Adds an event if the latest status event is an error.")
    Component(eventService, "EventService", "Service", "Queries and maps summaries, events and statistics.")
    Component(repository, "EventRepository", "Spring Data JPA", "Native SQL for summaries and statistics.")
    ContainerDb(db, "PostgreSQL", "History schema", "event, error")

    Rel(frontend, controller, "GET, POST", "HTTPS")
    Rel(controller, authService, "Checks access")
    Rel(authService, authz, "User access", "HTTP")
    Rel(controller, manual, "Manual action")
    Rel(controller, eventService, "Queries")
    Rel(manual, eventService, "Saves")
    Rel(eventService, repository, "Uses")
    Rel(repository, db, "SQL", "JDBC")

    UpdateRelStyle(frontend, controller, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(controller, authService, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(authService, authz, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(controller, manual, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(controller, eventService, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(manual, eventService, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(eventService, repository, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(repository, db, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateElementStyle(controller, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(authService, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(manual, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(eventService, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(repository, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(db, $bgColor="#C4B5FD", $fontColor="#1F2937", $borderColor="#8B5CF6")
    UpdateElementStyle(frontend, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(authz, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- Endpoints that return instance data, and the manual actions, check access. Unfiltered queries first read the distinct source application IDs
  from the database and then keep only those the user has access to.
- Manual actions (`instance-manually-processed`, `instance-manually-rejected`,
  `instance-status-overridden-as-transferred`) are stored as events. They are not published to
  Kafka.
- Invalid filters and payloads give 422 through `ValidationErrorsFormattingService`.

### Event history and background jobs

```mermaid
C4Component
    title Event history and background jobs

    Component(listeners, "Event listeners", "flyt-kafka", "One listener per event category, plus instance-error.")
    ContainerQueue_Ext(kafka, "Kafka", "Topics", "Instance flow events, archive-instance-id, instance-flow-headers")
    Component(registration, "Info and error event services", "Service", "Map a record and its headers to an event.")
    Component(requestReply, "Request/reply consumers", "flyt-kafka", "Latest archive instance ID and instance flow headers.")
    Component(repository, "EventRepository", "Spring Data JPA", "Native SQL for summaries and statistics.")
    Component(eventService, "EventService", "Service", "Queries and maps summaries, events and statistics.")
    ContainerDb(db, "PostgreSQL", "History schema", "event, error")
    Component(jobs, "Scheduled jobs", "Spring @Scheduled", "StatisticsMetricsPublisher, ScheduledErrorScrubService.")

    Rel(kafka, listeners, "Consumes")
    Rel(listeners, registration, "Registers")
    Rel(registration, repository, "Saves")
    BiRel(kafka, requestReply, "Request, reply")
    Rel(requestReply, eventService, "Looks up")
    Rel(eventService, repository, "Uses")
    Rel(repository, db, "SQL", "JDBC")
    Rel(jobs, eventService, "Statistics")
    Rel(jobs, repository, "Scrubs")

    UpdateRelStyle(kafka, listeners, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(listeners, registration, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(registration, repository, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(kafka, requestReply, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(requestReply, eventService, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(eventService, repository, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(repository, db, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(jobs, eventService, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(jobs, repository, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(listeners, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(registration, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(requestReply, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(repository, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(eventService, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(jobs, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(db, $bgColor="#C4B5FD", $fontColor="#1F2937", $borderColor="#8B5CF6")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- `EventListenerConfiguration` creates the listeners from `EventCategory`. Info events are stored
  with headers, timestamp and the origin application ID; error events also store the
  `ErrorCollection`. A record that fails is skipped without retry.
- `instance-error` carries the origin of the error, which decides the event category. The five
  older error topics are read while `legacy-error-topic-listeners-enabled` is true.
- The `instance-deleted` listener does nothing for now. Error details are only scrubbed by
  `ScheduledErrorScrubService`, once a day, after the retention period.
- `StatisticsMetricsPublisher` publishes the statistics as Prometheus gauges every minute. See
  [Metrics](../README.md#metrics).

### Instance registration

Off by default (`novari.flyt.instance-functionality.enabled`). The rules for positions, retries and
skipping records are in [Instance registration](../README.md#instance-registration).

```mermaid
C4Component
    title Instance registration

    ContainerQueue_Ext(kafkaIn, "Kafka", "Topic", "instance-received")
    ContainerQueue_Ext(kafkaOut, "Kafka", "Topic", "instance-registered")
    Component(consumer, "Instance-received consumer", "fint-kafka", "Own consumer group. Retries every 10 s, never skips.")
    Component(producer, "Producer", "flyt-kafka", "Publishes instance-registered and waits for the broker ack.")
    Component(registration, "Registration", "Service", "InstanceRegistrationService. Validates, saves, publishes, then moves the position.")
    Component(positions, "Positions", "Component", "InstanceReceivedPositions. next_offset per partition. Seeks on every assignment.")
    Component(instances, "InstanceService", "Spring Data JPA", "Saves the instance graph.")
    Component(errors, "Error events", "Service", "InstanceErrorEventService. Stores the registration error.")
    ContainerDb(db, "PostgreSQL", "Two schemas", "Instances. Events and instance_received_offset.")

    Rel(kafkaIn, consumer, "Consumes")
    Rel(producer, kafkaOut, "Publishes")
    Rel(consumer, registration, "register()")
    Rel(registration, producer, "Publishes")
    Rel(registration, positions, "isNext, advanced")
    Rel(registration, instances, "Saves")
    Rel(registration, errors, "Invalid input")
    Rel(instances, db, "SQL", "JDBC")
    Rel(errors, db, "SQL", "JDBC")

    UpdateRelStyle(kafkaIn, consumer, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(producer, kafkaOut, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(consumer, registration, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(registration, producer, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(registration, positions, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(registration, instances, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(registration, errors, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(instances, db, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(errors, db, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(consumer, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(producer, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(registration, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(positions, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(instances, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(errors, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(db, $bgColor="#C4B5FD", $fontColor="#1F2937", $borderColor="#8B5CF6")
    UpdateElementStyle(kafkaIn, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(kafkaOut, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

For each record, in this order:

1. `InstanceReceivedPositions` decides whether the record is the next one for its partition.
2. `InstanceService` saves the instance in the instance-service schema.
3. `InstanceRegisteredEventProducerService` publishes `instance-registered` and waits for the broker acknowledgement.
4. `next_offset` is set to the record offset + 1 in `instance_received_offset`.

Invalid input is stored as `INSTANCE_REGISTRATION_ERROR` together with the new `next_offset` in one
transaction. The consumer calls `InstanceReceivedPositions` on every partition assignment to seek
to `next_offset` (not drawn). `InstanceRegistrationService` and `InstanceReceivedPositions` read and write
`instance_received_offset` through `InstanceReceivedOffsetRepository` (not drawn).

### Instance deletion and cleanup

The instance functionality flag enables this flow. Registration headers for cleanup are read from local history.

```mermaid
C4Component
    title Instance deletion and cleanup

    ContainerQueue_Ext(dispatchedTopic, "Kafka", "Topic", "instance-dispatched")
    ContainerQueue_Ext(deletedTopic, "Kafka", "Topic", "instance-deleted")
    Component(consumer, "Instance-dispatched consumer", "flyt-kafka", "Own consumer group. No retries; skips failed records.")
    Component(cleanup, "InstanceCleanupService", "Spring @Scheduled", "Finds old root instances. Shared retention, default 60 days.")
    Component(deletion, "InstanceDeletionService", "Service", "Deletes the graph, then publishes the deletion event.")
    Component(instances, "InstanceService", "Spring Data JPA", "Finds old roots and deletes complete graphs in transactions.")
    Component(headers, "EventService", "Service", "Latest registration headers from local history.")
    Component(producer, "InstanceDeletedEventProducerService", "flyt-kafka", "Publishes asynchronously with the original headers and null payload.")
    ContainerDb(db, "PostgreSQL", "Two schemas", "Instance graphs and event history.")

    Rel(dispatchedTopic, consumer, "Consumes")
    Rel(consumer, deletion, "Deletes dispatched instance")
    Rel(cleanup, instances, "Finds old roots; deletes when headers are missing")
    Rel(cleanup, headers, "Looks up registration headers")
    Rel(cleanup, deletion, "Deletes with headers")
    Rel(deletion, instances, "Deletes graph")
    Rel(deletion, producer, "Publishes after commit")
    Rel(producer, deletedTopic, "Publishes")
    Rel(instances, db, "Reads, deletes", "JDBC")
    Rel(headers, db, "Reads via EventRepository", "JDBC")

    UpdateElementStyle(consumer, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(cleanup, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(deletion, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(instances, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(headers, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(producer, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(db, $bgColor="#C4B5FD", $fontColor="#1F2937", $borderColor="#8B5CF6")
    UpdateElementStyle(dispatchedTopic, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(deletedTopic, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

The dispatched consumer uses Kafka commits and a separate group from the ordinary history listener.
A failed delete leaves the instance for later scheduled cleanup. If publication fails after deletion,
the event may be lost. Cleanup without registration headers deletes the graph and logs a warning.
Immediate error-detail scrubbing remains disabled; scheduled scrubbing runs independently.
