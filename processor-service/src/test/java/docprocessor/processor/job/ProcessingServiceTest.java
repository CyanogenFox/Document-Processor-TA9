package docprocessor.processor.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessStatus;
import docprocessor.processor.analysis.DocumentAnalyzer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Plain JUnit tests for the processor core; every object is built by hand. */
@Slf4j
class ProcessingServiceTest {

    private final List<JobEvent> events = new CopyOnWriteArrayList<>();
    private final InMemoryProcessJobRepository repository = new InMemoryProcessJobRepository();
    private JobQueue queue;

    @AfterEach
    void shutDownPool() {
        queue.shutdown();
    }

    // Wires the service by hand with a pool of the given size.
    private ProcessingService service(int workers, int capacity, DocumentAnalyzer analyzer) {
        queue = new JobQueue(workers, capacity);
        JobWorker worker = new JobWorker(repository, analyzer, events::add);
        return new ProcessingService(repository, queue, worker);
    }

    private static ProcessRequest request(int documentCount) {
        List<DocumentInput> documents = IntStream.range(0, documentCount)
                .mapToObj(i -> new DocumentInput("doc-" + i, "hello world hello " + i))
                .toList();
        return new ProcessRequest(UUID.randomUUID(), documents);
    }

    private void awaitStatus(UUID jobId, ProcessStatus status, ProcessingService service) {
        await().atMost(Duration.ofSeconds(60))
                .until(() -> service.get(jobId).status() == status);
    }

    // Analyzer that blocks every call until the latch is released.
    private static DocumentAnalyzer blockingAnalyzer(CountDownLatch release) {
        return new DocumentAnalyzer() {
            @Override
            public DocumentResult analyze(DocumentInput document) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.analyze(document);
            }
        };
    }

    @Test
    void submit_newJob_completesWithResultsAndEvents() {
        ProcessingService service = service(2, 2, new DocumentAnalyzer());
        ProcessRequest request = request(3);

        ProcessingService.Submission submission = service.submit(request);
        awaitStatus(request.jobId(), ProcessStatus.COMPLETED, service);

        assertThat(submission.created()).isTrue();
        assertThat(service.get(request.jobId()).results()).hasSize(3);
        await().until(() -> events.size() == 2);
        assertThat(events).extracting(JobEvent::type)
                .containsExactly(JobEventType.STARTED, JobEventType.COMPLETED);
    }

    @Test
    void submit_sameJobIdTwice_runsOnce() {
        AtomicInteger calls = new AtomicInteger();
        ProcessingService service = service(2, 2, new DocumentAnalyzer() {
            @Override
            public DocumentResult analyze(DocumentInput document) {
                calls.incrementAndGet();
                return super.analyze(document);
            }
        });
        ProcessRequest request = request(3);

        ProcessingService.Submission first = service.submit(request);
        ProcessingService.Submission second = service.submit(request);
        awaitStatus(request.jobId(), ProcessStatus.COMPLETED, service);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(calls).hasValue(3);
    }

    @Test
    void submit_queueFull_throwsQueueFull() {
        CountDownLatch release = new CountDownLatch(1);
        ProcessingService service = service(1, 1, blockingAnalyzer(release));
        service.submit(request(1));
        service.submit(request(1));
        ProcessRequest rejected = request(1);

        try {
            assertThatThrownBy(() -> service.submit(rejected)).isInstanceOf(QueueFullException.class);
            assertThat(repository.findById(rejected.jobId())).isEmpty();
        } finally {
            release.countDown();
        }
    }

    @Test
    void submit_analyzerThrowsOnSecondDocument_jobFailedAndRestSkipped() {
        AtomicInteger calls = new AtomicInteger();
        ProcessingService service = service(1, 1, new DocumentAnalyzer() {
            @Override
            public DocumentResult analyze(DocumentInput document) {
                calls.incrementAndGet();
                if (document.id().equals("doc-1")) {
                    throw new IllegalStateException("boom");
                }
                return super.analyze(document);
            }
        });
        ProcessRequest request = request(3);

        service.submit(request);
        awaitStatus(request.jobId(), ProcessStatus.FAILED, service);

        ProcessJob job = service.get(request.jobId());
        assertThat(job.failureReason()).isEqualTo("PROCESSING_ERROR");
        assertThat(job.results()).isNullOrEmpty();
        assertThat(calls).hasValue(2);
        await().until(() -> events.size() == 2);
        assertThat(events).extracting(JobEvent::type)
                .containsExactly(JobEventType.STARTED, JobEventType.FAILED);
    }

    @Test
    void submit_tenJobsOfThousandDocuments_neverMoreThanTenAtOnce() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        ProcessingService service = service(10, 20, new DocumentAnalyzer() {
            @Override
            public DocumentResult analyze(DocumentInput document) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    return super.analyze(document);
                } finally {
                    running.decrementAndGet();
                }
            }
        });
        List<ProcessRequest> requests = IntStream.range(0, 10).mapToObj(i -> request(1000)).toList();

        long start = System.nanoTime();
        requests.forEach(service::submit);
        requests.forEach(r -> awaitStatus(r.jobId(), ProcessStatus.COMPLETED, service));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        log.info("10 jobs x 1000 documents finished in {} ms", elapsedMs);
        assertThat(maxRunning.get()).isLessThanOrEqualTo(10);
    }

    @Test
    void submit_publisherThrows_jobStillCompletes() {
        queue = new JobQueue(1, 1);
        JobWorker worker = new JobWorker(repository, new DocumentAnalyzer(), event -> {
            throw new IllegalStateException("publisher down");
        });
        ProcessingService service = new ProcessingService(repository, queue, worker);
        ProcessRequest request = request(2);

        service.submit(request);
        awaitStatus(request.jobId(), ProcessStatus.COMPLETED, service);

        assertThat(service.get(request.jobId()).results()).hasSize(2);
    }

    @Test
    void submit_moreJobsThanWorkers_neverMoreThanTenAtOnce() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch tenInFlight = new CountDownLatch(10);
        ProcessingService service = service(10, 20, new DocumentAnalyzer() {
            @Override
            public DocumentResult analyze(DocumentInput document) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    // Holds the first ten calls until all ten are running together.
                    tenInFlight.countDown();
                    tenInFlight.await(30, TimeUnit.SECONDS);
                    return super.analyze(document);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    running.decrementAndGet();
                }
            }
        });
        List<ProcessRequest> requests = IntStream.range(0, 20).mapToObj(i -> request(3)).toList();

        requests.forEach(service::submit);
        requests.forEach(r -> awaitStatus(r.jobId(), ProcessStatus.COMPLETED, service));

        assertThat(maxRunning).hasValue(10);
    }

    @Test
    void delete_activeJob_throwsActive() {
        CountDownLatch release = new CountDownLatch(1);
        ProcessingService service = service(1, 1, blockingAnalyzer(release));
        ProcessRequest request = request(1);
        service.submit(request);

        try {
            assertThatThrownBy(() -> service.delete(request.jobId()))
                    .isInstanceOf(ProcessJobActiveException.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void delete_unknownJob_throwsNotFound() {
        ProcessingService service = service(1, 1, new DocumentAnalyzer());

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(ProcessJobNotFoundException.class);
    }
}
