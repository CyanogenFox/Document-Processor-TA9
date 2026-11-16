package docprocessor.job.controller.dto;

import docprocessor.contracts.DocumentResult;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A job with its results, as returned by POST and GET on one job. */
public record JobResponse(
        UUID id,
        JobStatus status,
        int documentCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<DocumentResult> results) {

    public static JobResponse from(Job job) {
        return new JobResponse(job.id(), job.status(), job.documentCount(), job.failureReason(),
                job.createdAt(), job.updatedAt(), job.results());
    }
}
