package docprocessor.processor.job;

import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.ProcessStatus;
import java.util.List;
import java.util.UUID;

/** Stored state of one job; results are set only when COMPLETED, failureReason only when FAILED. */
public record ProcessJob(UUID jobId, ProcessStatus status, String failureReason, List<DocumentResult> results) {
}
