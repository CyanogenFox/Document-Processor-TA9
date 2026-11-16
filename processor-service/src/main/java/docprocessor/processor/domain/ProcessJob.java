package docprocessor.processor.domain;

import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.ProcessResponse;
import docprocessor.contracts.ProcessStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stored state of one job; results are set only when COMPLETED, failureReason only when FAILED, finishedAt when finished. */
public record ProcessJob(UUID jobId, ProcessStatus status, String failureReason, List<DocumentResult> results,
                         Instant finishedAt) {

    public ProcessResponse toResponse() {
        return new ProcessResponse(jobId, status, failureReason, results);
    }
}
