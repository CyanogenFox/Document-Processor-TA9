package docprocessor.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import docprocessor.contracts.ProcessResponse;
import docprocessor.contracts.ProcessStatus;
import docprocessor.job.StubProcessor;
import docprocessor.job.StubProcessor.Reply;
import docprocessor.job.domain.FailureReason;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "job.poller.enabled=false")
class JobSyncTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final StubProcessor STUB = new StubProcessor();
    private static final String GET = "GET";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job.processor.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @TestBean(name = "clock")
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @MockitoBean
    private JobDispatcher dispatcher;
    @Autowired
    private JobSync jobSync;
    @Autowired
    private JobRepository repository;
    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetStub() {
        STUB.reset();
    }

    private Job saveJob(JobStatus status, Duration age) {
        Instant updated = NOW.minus(age);
        Job job = Job.builder().id(UUID.randomUUID()).status(status).documentCount(1)
                .createdAt(updated).updatedAt(updated).dispatchedAt(updated).build();
        repository.save(job);
        return job;
    }

    private Job staleJob(JobStatus status) {
        return saveJob(status, Duration.ofSeconds(11));
    }

    private Job stored(Job job) {
        return repository.findById(job.id()).orElseThrow();
    }

    private String path(Job job) {
        return "/api/process/" + job.id();
    }

    private void scriptGet(Job job, ProcessStatus status) throws Exception {
        STUB.script(GET, path(job), Reply.of(200,
                jsonMapper.writeValueAsString(new ProcessResponse(job.id(), status, null, List.of()))));
        STUB.script("DELETE", path(job), Reply.of(204, ""));
    }

    private void scriptProcessorDown(Job job) {
        STUB.script(GET, path(job), Reply.of(503, "{}"));
    }

    @Test
    void syncStale_missedCompletedEvent_jobCompleted() throws Exception {
        Job job = staleJob(JobStatus.PROCESSING);
        scriptGet(job, ProcessStatus.COMPLETED);

        jobSync.syncStale();

        assertThat(stored(job).status()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void syncStale_missedStartedEvent_jobProcessing() throws Exception {
        Job job = staleJob(JobStatus.PENDING);
        scriptGet(job, ProcessStatus.PROCESSING);

        jobSync.syncStale();

        assertThat(stored(job).status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void syncStale_processorAnswers404_jobFailedProcessorLost() {
        Job job = staleJob(JobStatus.PROCESSING);

        jobSync.syncStale();

        assertThat(stored(job).status()).isEqualTo(JobStatus.FAILED);
        assertThat(stored(job).failureReason()).isEqualTo(FailureReason.PROCESSOR_LOST);
    }

    @Test
    void syncStale_freshJob_notPolled() {
        Job job = saveJob(JobStatus.PROCESSING, Duration.ofSeconds(1));

        jobSync.syncStale();

        assertThat(STUB.requests(GET, path(job))).isEmpty();
        assertThat(stored(job).status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void syncStale_processorDownWithinGiveUp_jobUnchanged() {
        Job job = staleJob(JobStatus.PROCESSING);
        scriptProcessorDown(job);

        jobSync.syncStale();

        assertThat(stored(job)).isEqualTo(job);
    }

    @Test
    void syncStale_processorDownPastGiveUp_jobFailedUnavailable() {
        Job job = saveJob(JobStatus.PROCESSING, Duration.ofMinutes(6));
        scriptProcessorDown(job);

        jobSync.syncStale();

        assertThat(stored(job).status()).isEqualTo(JobStatus.FAILED);
        assertThat(stored(job).failureReason()).isEqualTo(FailureReason.PROCESSOR_UNAVAILABLE);
    }

    private Job savePending(Duration age) {
        Instant updated = NOW.minus(age);
        Job job = Job.builder().id(UUID.randomUUID()).status(JobStatus.PENDING).documentCount(1)
                .createdAt(updated).updatedAt(updated).build();
        repository.save(job);
        return job;
    }

    @Test
    void syncStale_stalePendingJob_redispatched() {
        Job job = savePending(Duration.ofSeconds(11));

        jobSync.syncStale();

        verify(dispatcher).dispatch(job);
        assertThat(stored(job).status()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void syncStale_processingJobNeverAccepted_redispatched() {
        Job pending = savePending(Duration.ofSeconds(11));
        Job job = pending.toBuilder().status(JobStatus.PROCESSING).build();
        repository.save(job);

        jobSync.syncStale();

        verify(dispatcher).dispatch(job);
        assertThat(stored(job).status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void syncStale_freshPendingJob_notRedispatched() {
        Job job = savePending(Duration.ofSeconds(1));

        jobSync.syncStale();

        verify(dispatcher, never()).dispatch(job);
    }

    @Test
    void syncStale_pendingPastGiveUp_jobFailedUnavailable() {
        Job job = savePending(Duration.ofMinutes(6));

        jobSync.syncStale();

        assertThat(stored(job).status()).isEqualTo(JobStatus.FAILED);
        assertThat(stored(job).failureReason()).isEqualTo(FailureReason.PROCESSOR_UNAVAILABLE);
        verify(dispatcher, never()).dispatch(job);
    }
}
