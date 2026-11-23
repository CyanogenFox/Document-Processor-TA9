# TA9 Document Processor

Two Spring Boot 4.1.2 services (Java 21). The Job Service takes jobs over REST. The Processor Service analyses the documents and reports back over WebSocket.

## 1. Build and run

**Docker Compose** (needs Docker):

```
docker compose up --build
```

- Job Service: http://localhost:8080. Processor Service: http://localhost:8081.
- One root `Dockerfile` with a `MODULE` argument builds each service.
- Compose uses `depends_on` only, no health checks. The Processor reconnects until the Job Service is up.
- Try it:

```
curl -i -X POST localhost:8080/api/jobs -H "Content-Type: application/json" \
  -d '{"documents":[{"id":"d1","text":"Hello world, hello"}]}'
curl localhost:8080/api/jobs/<id from the Location header>
```

- Postman collection: `postman/ta9-document-processor.postman_collection.json`. It covers create, get, list (filter, paging), delete, and invalid input. Each request has status checks. It runs with Newman: `npx newman run postman/ta9-document-processor.postman_collection.json`.
- Requests must send `Content-Length` (curl and Postman do). Chunked requests get 411.

**Plain Maven** (JDK 21, two terminals):

```
./mvnw package -DskipTests
java -jar processor-service/target/processor-service-0.0.1-SNAPSHOT-exec.jar
java -jar job-service/target/job-service-0.0.1-SNAPSHOT-exec.jar
```

Defaults point both services at `localhost`, so no settings are needed.

## 2. Run the tests

```
./mvnw verify
```

- Unit, per-service (other side stubbed) and end-to-end tests. Docker is not needed.
- Counts: Job Service 59, Processor Service 36, e2e 2.
- The e2e module runs both services in one JVM: the happy path, and a missed event recovered by the poller.

## 3. Architecture overview

```
Client --REST /api/jobs--> Job Service --REST /api/process--> Processor Service
                           Job Service <--WebSocket events--- Processor Service
                           Job Service --GET /api/process/{id} (results, poller)--> Processor Service
```

1. The client posts a job. The Job Service validates it, saves it as `PENDING`, returns 202.
2. A background task sends the job to the Processor. At most 10 sends run at once.
3. The Processor queues it, returns 202. A worker picks it up.
4. The Processor sends `STARTED`. The Job Service sets `PROCESSING`.
5. The Processor ends with `COMPLETED` or `FAILED`.
6. On a final event the Job Service reads the job's state over REST, applies it, then deletes the job at the Processor.
7. A poller re-checks jobs that have not moved for a while.

Rule: events are small notifications. REST is the truth.

| Module | Holds |
| --- | --- |
| `contracts` | Shared records: process request, results, events. No Spring. |
| `job-service` | REST API, job state, WebSocket server, Processor client, poller |
| `processor-service` | REST API, bounded workers, analyzer, WebSocket client |
| `e2e-tests` | Both services in one JVM |

- Layers in both services: controller, service, repository. Storage is a repository interface over a `ConcurrentHashMap`, so a database can replace it.
- Job states: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. A job never moves backwards.
- Job API: `POST /api/jobs` (202), `GET /api/jobs/{id}`, `GET /api/jobs?status=&page=&size=`, `DELETE /api/jobs/{id}` (finished jobs only, else 409). No `PUT`: a job is immutable.
- Processor API: `POST`, `GET`, `DELETE` on `/api/process`.
- Per document: word count, unique word count, top 5 words, SHA-256, character count, processing time in ms.
- Errors are RFC 9457 `ProblemDetail`. Codes: 400 invalid input, 404 unknown id, 409 delete of an active job, 411 no `Content-Length`, 413 too large, 503 Processor queue full.
- Health: `/actuator/health` on both services.
- Logs print the job id: `INFO [<jobId>]`, and `INFO []` off a job.

## 4. Key decisions and trade-offs

- **Spring MVC + plain WebSocket + bounded pools.** STOMP adds a broker setup for one one-way channel. WebFlux is harder to read and the analysis is CPU-bound.
- **Events are notifications, REST is the truth.** A lost or repeated event cannot corrupt state. Cost: one extra REST call per job.
- **The Job Service deletes the job at the Processor** after it has read the state. This keeps Processor memory small.
- **Dispatch is retry, then `FAILED`.** 3 retries (0.3, 0.6, 1.2 s). Retried: connection errors, timeouts, every 5xx. A 4xx fails at once (`PROCESSOR_REJECTED`). Exhausted retries give `PROCESSOR_UNAVAILABLE`.
- **Retry is Spring's `@Retryable`** on `ProcessorClient.submit`. No extra library.
- **Any other error in the dispatch task gives `INTERNAL_ERROR`.** No job stays `PENDING` forever.
- **A processing error fails the whole job** (`PROCESSING_ERROR`). Simple and predictable. Per-document errors are the better production design.
- **Size limit on the Job Service only.** 10 MB, read from `Content-Length` before the body (413, or 411 without the header). The Processor trusts its caller.
- **Duplicate document ids give 400.** All duplicates are reported in one error.
- **IDs are UUIDs made by the Job Service.** The Processor uses the same id.
- **A repeated `POST /api/process` for the same id returns 200** and does not run twice.
- **Services are called through interfaces** (`X` + `XImpl`). Callers never see the implementation.
- **No `@Transactional`.** There is no database. Atomic updates come from `ConcurrentHashMap` (`computeIfPresent`, `putIfAbsent`).
- **HTTP timeouts are set in code** (connect 2 s, read 5 s), like every other setting default.
- **Virtual threads for blocking I/O.** The 10 analysis workers are platform threads.
- **Lombok** for constructors and loggers only. DTOs are records.
- **Document text is dropped** once the next service has accepted or analysed it.
- **`GET /api/jobs` returns summaries without results.** Newest first, size capped at 100.
- **A failed event send never changes a job's state.** The Processor logs it and drops the event. The poller recovers the job about 10-15 s later.
- **After a normal WebSocket close, the Processor waits 1 s before reconnecting.** This avoids a hot reconnect loop. Events in that second are recovered by the poller.
- **The retry defaults live in the `@Retryable` annotation** (`${job.processor.max-retries:3}`). `@DefaultValue` defaults are not visible to `${...}` placeholders, so a copy in `JobProperties` would be a second default that can drift.
- **Small design choices in the Job Service** (all approved):
  - `PageResult` carries `page` and `size`, so the response shows the capped size.
  - `JobService.create` takes `List<DocumentInput>`, so the service layer imports no controller DTO.
  - A late "accepted" call is ignored on a finished job.
  - If recording "accepted" fails after the Processor took the job, the job becomes `INTERNAL_ERROR`. The Processor record stays (see section 8).
  - The 1,001-documents test has its own class, because the main controller test uses a 1 KB size limit.

## 5. Concurrency and performance

- **Job Service:** each dispatch runs on a virtual thread. A semaphore allows 10 sends in flight. More jobs wait as `PENDING`. The client already has its 202.
- **Processor:** one `ThreadPoolExecutor` in `JobQueue`. 10 platform threads, a queue of 20, and rejection when full (503 + `Retry-After`).
- **One job per thread.** Its documents run one after another. So at most 10 documents are in flight, and there are no nested pools.
- **Measured:** 10 jobs x 1,000 documents completed in 1094 ms on the dev machine (Processor test). Never more than 10 running.
- **Limits:**
  - A single job uses one core. Parallel documents per job are not built.
  - 10 running + 20 queued is the capacity. A burst beyond that gets 503 from the Processor, is retried, and can end `FAILED` after 4 attempts.
  - Time until `FAILED` when the Processor is bad (connect 2 s, read 5 s, 4 attempts): down about 2 s; host silent about 10 s; accepts but hangs about 22 s.
  - Everything is in memory.

## 6. WebSocket disconnect, reconnect and duplicates

- **While the connection is down,** the Processor drops events and logs it. It does not buffer them.
- **Reconnect:** backoff starts at 1 s, doubles, and is capped at 30 s. The delay resets after a connection.
- **Recovery:** the poller checks jobs not changed for 10 s, every 5 s. It reads the Processor's state over REST and applies it. A 404 from the Processor means it lost the job (restart): `FAILED` with `PROCESSOR_LOST`.
- **Give-up rule:** if the Processor is unreachable and the job has not changed for 5 minutes, it becomes `FAILED` (`PROCESSOR_UNAVAILABLE`).
- **Duplicates and stale events:** the state check ignores any change that is repeated or goes backwards. It is logged.
- **Bad input on the socket:** malformed JSON or an unknown `jobId` is logged and ignored. The connection stays open.
- **Silent loss:** if the Job Service's machine vanishes without closing the socket, the Processor notices only when a send fails. The poller recovers those jobs.

## 7. Settings

All have a default in code. Both services run with no config file. Override with a property or an environment variable (for example `JOB_PROCESSOR_BASE_URL`).

| Key | Default | What it does |
| --- | --- | --- |
| `job.max-request-size` | `10MB` | Largest accepted `POST /api/jobs` (from `Content-Length`) |
| `job.processor.base-url` | `http://localhost:8081` | Where the Processor is |
| `job.processor.max-concurrent-dispatches` | `10` | Sends to the Processor in flight |
| `job.processor.connect-timeout` | `2s` | HTTP connect timeout |
| `job.processor.read-timeout` | `5s` | HTTP read timeout |
| `job.processor.max-retries` | `3` | Retries of a submit (placeholder default in `@Retryable`) |
| `job.processor.retry-delay-ms` | `300` | First retry delay in ms, doubles each time (same) |
| `job.poller.enabled` | `true` | Turns the poller on or off |
| `job.poller.interval` | `5s` | Time between poller runs |
| `job.poller.stale-after` | `10s` | A job unchanged this long is re-checked |
| `job.poller.give-up-after` | `5m` | Unreachable and unchanged this long: `FAILED` |
| `processor.workers` | `10` | Analysis threads |
| `processor.queue-capacity` | `20` | Jobs waiting for a worker |
| `processor.job-service-ws-url` | `ws://localhost:8080/ws/events` | Where events are sent |
| `processor.reconnect-delay` | `1s` | First reconnect delay |
| `processor.max-reconnect-delay` | `30s` | Reconnect delay cap |

Fixed in `application.yml`: `server.port` (8080 and 8081), virtual threads on, problem details on, the log pattern, and `health` exposure.

## 8. Known limits and production improvements

**Known limits**

- The Processor is internal and trusts the Job Service. In production it gets its own size limit.
- Requests must send `Content-Length`. Streaming uploads belong to a chunked upload API.
- Give-up weak point: a job that has run for more than 5 minutes fails on a single failed poll. Jobs take seconds, so this is far from normal use.
- The three timing cases in section 5.
- A burst can still end in `FAILED` after 4 attempts.
- Silent connection loss is noticed late (section 6).
- If the Job Service's `DELETE` to the Processor never arrives (it failed, or the Job Service restarted), the record stays in the Processor.
- Chinese and Japanese text has no spaces, so a run of characters counts as one word.
- Jobs in flight are lost on restart.

**Production improvements**

- Per-document errors (preferred).
- Chunked upload API: create a job, add documents in parts, start it.
- Admission control: refuse new jobs (429) when too many are waiting.
- The Processor expires finished records.
- Parallel documents per job on a shared CPU pool, with fairness.
- Progress events.
- `Idempotency-Key` on `POST /api/jobs`.
- Processor health shows WebSocket connection state.
- JSON logs, persistent storage, authentication.
- A job that stays `PENDING` while the Processor is down, with the poller re-dispatching it, instead of failing after retries.

## 9. Not done

- No planned item was cut.
- Optional tasks not built: staying `PENDING` after failed retries, expiry of Processor records, per-document errors. The bonus Audit Service is not built either.

## 10. AI usage

**Tools**

- Claude Code in VS Code. Claude Opus 5.5 was the controller. Sonnet and Haiku subagents implemented and reviewed tasks.
- Claude (claude.ai project) for the design, the plan, and the step-by-step execution of the plan.

**What for**

- A design and a plan were written before Claude generated any code.
- Then the code was built task by task. One agent implemented each task and a fresh reviewer agent checked it. I review and push the work at each checkpoint.
- AI-generated or AI-assisted: nearly all production code, the tests, the Dockerfile, the Postman collection and this README.

**Representative prompts**

- "Execute the plan with subagent-driven development; stop after Task 6."
- "Every duplicate document id must be reported, not just the first."
- "Move the HTTP timeouts into code."
- "Each service must be an interface plus an Impl, called only through the interface."

**Examples of suggestions I changed or rejected**

- Claude proposed no pagination. I added it since the number of jobs can grow large.
- Claude proposed a per-document size cap. I replaced it with a cap on the whole job.
- Claude proposed Java 17. I chose Java 21, for virtual threads.
- Claude proposed "retry then fail" as final. I made it step one, with "stay pending" as a planned upgrade.
- Claude proposed no Lombok. I chose Lombok.
- The plan used the package layout `job/`, `web/`. I changed it to `domain/`, `repository/`, `service/`, `controller/`.
- The plan used concrete service classes. I required an interface plus an Impl for each.
- The duplicate-id check stopped at the first duplicate. I asked for all of them.
- The job id was in the log context but never printed. I chose to add it to the log pattern.

**Skills installed and used**

- `superpowers` (brainstorming, writing-plans, subagent-driven-development).
- Planning: `layered-architecture`, `configuration-properties`, `async-execution`, `idempotency-patterns`, `scheduled-tasks`.
- Build: `resilience-retry` (retry), `scheduled-tasks` (poller), `container-native-deployment` (Dockerfile).
