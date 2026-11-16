package docprocessor.job.events;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import docprocessor.contracts.ProcessResponse;
import docprocessor.contracts.ProcessStatus;
import docprocessor.job.StubProcessor;
import docprocessor.job.StubProcessor.Reply;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import docprocessor.job.service.JobDispatcher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "job.poller.enabled=false")
class JobEventHandlerTest {

    private static final StubProcessor STUB = new StubProcessor();
    private static final String GET = "GET";
    private static final String DELETE = "DELETE";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job.processor.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @MockitoBean
    private JobDispatcher dispatcher;
    @Autowired
    private JobRepository repository;
    @Autowired
    private JsonMapper jsonMapper;
    @LocalServerPort
    private int port;

    private WebSocketSession session;

    @BeforeEach
    void connect() throws Exception {
        STUB.reset();
        session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), "ws://localhost:" + port + "/ws/events").get(5, SECONDS);
    }

    @AfterEach
    void disconnect() throws Exception {
        session.close();
    }

    private Job saveJob(JobStatus status, Instant dispatchedAt) {
        Instant now = Instant.now();
        Job job = Job.builder().id(UUID.randomUUID()).status(status).documentCount(1)
                .createdAt(now).updatedAt(now).dispatchedAt(dispatchedAt).build();
        repository.save(job);
        return job;
    }

    private Job processingJob() {
        return saveJob(JobStatus.PROCESSING, Instant.now());
    }

    private Job stored(Job job) {
        return repository.findById(job.id()).orElseThrow();
    }

    private void send(UUID id, JobEventType type) throws Exception {
        session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(new JobEvent(id, type, Instant.now(), null))));
    }

    private String path(Job job) {
        return "/api/process/" + job.id();
    }

    private DocumentResult result(String id) {
        return new DocumentResult(id, 3, 3, List.of(), "abc", 10, 1, null);
    }

    private void scriptGet(Job job, ProcessStatus status, String reason, List<DocumentResult> results) throws Exception {
        STUB.script(GET, path(job), Reply.of(200,
                jsonMapper.writeValueAsString(new ProcessResponse(job.id(), status, reason, results))));
        STUB.script(DELETE, path(job), Reply.of(204, ""));
    }

    @Test
    void started_pendingJob_becomesProcessing() throws Exception {
        Job job = saveJob(JobStatus.PENDING, Instant.now());

        send(job.id(), JobEventType.STARTED);

        await().untilAsserted(() -> assertThat(stored(job).status()).isEqualTo(JobStatus.PROCESSING));
    }

    @Test
    void completed_processingJob_fetchesResultsThenCompletes() throws Exception {
        Job job = processingJob();
        scriptGet(job, ProcessStatus.COMPLETED, null, List.of(result("a")));

        send(job.id(), JobEventType.COMPLETED);

        await().untilAsserted(() -> {
            assertThat(stored(job).status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(stored(job).results()).containsExactly(result("a"));
            assertThat(STUB.requests(DELETE, path(job))).hasSize(1);
        });
    }

    @Test
    void completed_beforeAccepted_stillCompletes() throws Exception {
        Job job = saveJob(JobStatus.PENDING, null);
        scriptGet(job, ProcessStatus.COMPLETED, null, List.of(result("a")));

        send(job.id(), JobEventType.COMPLETED);

        await().untilAsserted(() -> assertThat(stored(job).status()).isEqualTo(JobStatus.COMPLETED));
    }

    @Test
    void completed_twice_secondIsHarmless() throws Exception {
        Job job = processingJob();
        scriptGet(job, ProcessStatus.COMPLETED, null, List.of(result("a")));
        send(job.id(), JobEventType.COMPLETED);
        await().untilAsserted(() -> assertThat(stored(job).status()).isEqualTo(JobStatus.COMPLETED));
        STUB.script(GET, path(job), Reply.of(200, jsonMapper.writeValueAsString(
                new ProcessResponse(job.id(), ProcessStatus.COMPLETED, null, List.of(result("other"))))));

        send(job.id(), JobEventType.COMPLETED);
        Job marker = saveJob(JobStatus.PENDING, Instant.now());
        send(marker.id(), JobEventType.STARTED);

        await().untilAsserted(() -> assertThat(stored(marker).status()).isEqualTo(JobStatus.PROCESSING));
        assertThat(stored(job).status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(stored(job).results()).containsExactly(result("a"));
    }

    @Test
    void failed_processingJob_fetchesStateThenFails() throws Exception {
        Job job = processingJob();
        scriptGet(job, ProcessStatus.FAILED, "PROCESSING_ERROR", null);

        session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(
                new JobEvent(job.id(), JobEventType.FAILED, Instant.now(), "event reason"))));

        await().untilAsserted(() -> {
            assertThat(stored(job).status()).isEqualTo(JobStatus.FAILED);
            assertThat(stored(job).failureReason()).isEqualTo("PROCESSING_ERROR");
            assertThat(STUB.requests(DELETE, path(job))).hasSize(1);
        });
    }

    @Test
    void event_unknownJobId_ignoredAndConnectionOpen() throws Exception {
        send(UUID.randomUUID(), JobEventType.STARTED);
        send(UUID.randomUUID(), JobEventType.COMPLETED);

        assertConnectionStillWorks();
    }

    @Test
    void event_malformedJson_ignoredAndConnectionOpen() throws Exception {
        session.sendMessage(new TextMessage("this is not json"));
        session.sendMessage(new TextMessage("{\"jobId\":\"" + UUID.randomUUID() + "\",\"type\":\"EXPLODED\"}"));
        session.sendMessage(new TextMessage("{}"));

        assertConnectionStillWorks();
    }

    private void assertConnectionStillWorks() throws Exception {
        Job job = saveJob(JobStatus.PENDING, Instant.now());
        send(job.id(), JobEventType.STARTED);

        await().untilAsserted(() -> assertThat(stored(job).status()).isEqualTo(JobStatus.PROCESSING));
        assertThat(session.isOpen()).isTrue();
    }
}
