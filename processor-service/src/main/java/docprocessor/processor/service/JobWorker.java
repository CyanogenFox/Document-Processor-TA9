package docprocessor.processor.service;
import docprocessor.processor.repository.ProcessJobRepository;
import docprocessor.processor.domain.ProcessJob;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessStatus;
import docprocessor.processor.analysis.DocumentAnalyzer;
import docprocessor.processor.events.JobEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Runs one job: analyzes its documents one after another and records the outcome. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    static final String FAILURE_REASON = "PROCESSING_ERROR";

    private final ProcessJobRepository repository;
    private final DocumentAnalyzer analyzer;
    private final JobEventPublisher publisher;
    private final Clock clock;

    public void run(ProcessRequest request) {
        MDC.put("jobId", request.jobId().toString());
        try {
            changeState(request, ProcessStatus.PROCESSING, null, null);
            publish(request, JobEventType.STARTED, null);

            List<DocumentResult> results = analyzeAll(request);
            if (results.stream().allMatch(r -> r.error() != null)) {
                changeState(request, ProcessStatus.FAILED, FAILURE_REASON, null);
                publish(request, JobEventType.FAILED, FAILURE_REASON);
            } else {
                changeState(request, ProcessStatus.COMPLETED, null, results);
                publish(request, JobEventType.COMPLETED, null);
            }
        } finally {
            MDC.clear();
        }
    }

    // One result per document in input order; a failing document gets an error result (logged, never detailed).
    private List<DocumentResult> analyzeAll(ProcessRequest request) {
        List<DocumentResult> results = new ArrayList<>();
        for (DocumentInput document : request.documents()) {
            try {
                results.add(analyzer.analyze(document));
            } catch (RuntimeException e) {
                log.error("Analysis failed for job {} document {}", request.jobId(), document.id(), e);
                results.add(DocumentResult.failed(document.id(), FAILURE_REASON));
            }
        }
        return results;
    }

    private void changeState(ProcessRequest request, ProcessStatus status, String reason,
                             List<DocumentResult> results) {
        boolean finished = status == ProcessStatus.COMPLETED || status == ProcessStatus.FAILED;
        Instant finishedAt = finished ? clock.instant() : null;
        repository.save(new ProcessJob(request.jobId(), status, reason, results, finishedAt));
        log.info("Job {} is now {}", request.jobId(), status);
    }

    // Events are notifications only: a publisher failure is logged and never changes the job.
    private void publish(ProcessRequest request, JobEventType type, String reason) {
        try {
            publisher.publish(new JobEvent(request.jobId(), type, clock.instant(), reason));
        } catch (RuntimeException e) {
            log.warn("Could not publish {} event for job {}", type, request.jobId(), e);
        }
    }
}
