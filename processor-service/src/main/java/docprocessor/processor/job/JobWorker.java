package docprocessor.processor.job;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessStatus;
import docprocessor.processor.analysis.DocumentAnalyzer;
import docprocessor.processor.events.JobEventPublisher;
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

    public void run(ProcessRequest request) {
        MDC.put("jobId", request.jobId().toString());
        try {
            changeState(request, ProcessStatus.PROCESSING, null, null);
            publish(request, JobEventType.STARTED, null);

            List<DocumentResult> results = analyzeAll(request);
            if (results == null) {
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

    // Returns all results, or null if a document failed (the error is logged, never returned).
    private List<DocumentResult> analyzeAll(ProcessRequest request) {
        List<DocumentResult> results = new ArrayList<>();
        for (DocumentInput document : request.documents()) {
            try {
                results.add(analyzer.analyze(document));
            } catch (RuntimeException e) {
                log.error("Analysis failed for job {} document {}", request.jobId(), document.id(), e);
                return null;
            }
        }
        return results;
    }

    private void changeState(ProcessRequest request, ProcessStatus status, String reason,
                             List<DocumentResult> results) {
        repository.save(new ProcessJob(request.jobId(), status, reason, results));
        log.info("Job {} is now {}", request.jobId(), status);
    }

    // Events are notifications only: a publisher failure is logged and never changes the job.
    private void publish(ProcessRequest request, JobEventType type, String reason) {
        try {
            publisher.publish(new JobEvent(request.jobId(), type, Instant.now(), reason));
        } catch (RuntimeException e) {
            log.warn("Could not publish {} event for job {}", type, request.jobId(), e);
        }
    }
}
