package docprocessor.contracts;

import java.util.List;
import java.util.UUID;

/** Current state and results of a processing job. */
public record ProcessResponse(
        UUID jobId,
        ProcessStatus status,
        String failureReason,
        List<DocumentResult> results) {
}
