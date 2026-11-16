package docprocessor.job.service;

import docprocessor.contracts.DocumentResult;
import java.util.List;
import java.util.UUID;

/** The only place that changes a job status; each method returns true if the change was applied. */
public interface JobLifecycle {

    /** The Processor took the job: records the dispatch time and drops the documents. */
    boolean accepted(UUID id);

    /** PENDING to PROCESSING. */
    boolean started(UUID id);

    /** Finishes the job with results. */
    boolean completed(UUID id, List<DocumentResult> results);

    /** Finishes the job with a failure reason. */
    boolean failed(UUID id, String reason);
}
