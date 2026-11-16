package docprocessor.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.StubProcessor;
import docprocessor.job.StubProcessor.Reply;
import docprocessor.job.domain.FailureReason;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "job.processor.retry-delay-ms=10",
        "job.processor.max-concurrent-dispatches=2",
        "job.poller.enabled=false"})
class JobDispatcherTest {

    private static final StubProcessor STUB = new StubProcessor();
    private static final String POST = "POST";
    private static final String PATH = "/api/process";
    private static final String QUEUED = "{\"status\":\"QUEUED\"}";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job.processor.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Autowired
    private JobDispatcher dispatcher;
    @Autowired
    private JobRepository repository;

    @BeforeEach
    void resetStub() {
        STUB.reset();
    }

    private Job newJob() {
        Instant now = Instant.now();
        Job job = Job.builder().id(UUID.randomUUID()).status(JobStatus.PENDING)
                .documents(List.of(new DocumentInput("a", "hello"))).documentCount(1)
                .createdAt(now).updatedAt(now).build();
        repository.save(job);
        return job;
    }

    private Job stored(Job job) {
        return repository.findById(job.id()).orElseThrow();
    }

    private void awaitFailed(Job job, String reason) {
        await().untilAsserted(() -> {
            assertThat(stored(job).status()).isEqualTo(JobStatus.FAILED);
            assertThat(stored(job).failureReason()).isEqualTo(reason);
        });
    }

    @Test
    void dispatch_processorAccepts_jobAcceptedAndTextDropped() {
        Job job = newJob();

        dispatcher.dispatch(job);

        await().untilAsserted(() -> assertThat(stored(job).dispatchedAt()).isNotNull());
        assertThat(stored(job).documents()).isNull();
        assertThat(stored(job).status()).isEqualTo(JobStatus.PENDING);
        assertThat(STUB.requests(POST, PATH)).hasSize(1);
        assertThat(STUB.requests(POST, PATH).get(0).body()).contains(job.id().toString());
    }

    @Test
    void dispatch_503Then500Then202_jobAccepted() {
        STUB.script(POST, PATH, Reply.of(503, "{}"), Reply.of(500, "{}"), Reply.of(202, QUEUED));
        Job job = newJob();

        dispatcher.dispatch(job);

        await().untilAsserted(() -> assertThat(stored(job).dispatchedAt()).isNotNull());
        assertThat(STUB.requests(POST, PATH)).hasSize(3);
    }

    @Test
    void dispatch_always503_jobFailedAfterFourAttempts() {
        STUB.script(POST, PATH, Reply.of(503, "{}"));
        Job job = newJob();

        dispatcher.dispatch(job);

        awaitFailed(job, FailureReason.PROCESSOR_UNAVAILABLE);
        assertThat(STUB.requests(POST, PATH)).hasSize(4);
    }

    @Test
    void dispatch_processorReturns400_jobFailedWithoutRetry() {
        STUB.script(POST, PATH, Reply.of(400, "{}"));
        Job job = newJob();

        dispatcher.dispatch(job);

        awaitFailed(job, FailureReason.PROCESSOR_REJECTED);
        assertThat(STUB.requests(POST, PATH)).hasSize(1);
    }

    @Test
    void dispatch_processorAnswersGarbage_jobFailedInternalError() {
        STUB.script(POST, PATH, Reply.of(202, "this is not json"));
        Job job = newJob();

        dispatcher.dispatch(job);

        awaitFailed(job, FailureReason.INTERNAL_ERROR);
        assertThat(STUB.requests(POST, PATH)).hasSize(1);
    }

    @Test
    void dispatch_moreJobsThanPermits_neverMoreThanLimitInFlight() {
        STUB.script(POST, PATH, new Reply(202, QUEUED, 200));
        List<Job> jobs = IntStream.range(0, 6).mapToObj(i -> newJob()).toList();

        jobs.forEach(dispatcher::dispatch);

        await().untilAsserted(() -> assertThat(jobs)
                .allSatisfy(j -> assertThat(stored(j).dispatchedAt()).isNotNull()));
        assertThat(STUB.requests(POST, PATH)).hasSize(6);
        assertThat(STUB.maxInFlight()).isEqualTo(2);
    }
}
