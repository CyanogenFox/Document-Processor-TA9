package docprocessor.job.domain;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** An immutable snapshot of a job; documents are dropped once they are no longer needed. */
@Builder(toBuilder = true)
public record Job(
        UUID id,
        JobStatus status,
        List<DocumentInput> documents,
        int documentCount,
        List<DocumentResult> results,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant dispatchedAt) {
}
