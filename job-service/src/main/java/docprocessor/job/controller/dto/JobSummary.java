package docprocessor.job.controller.dto;

import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;

/** A job without its results, as listed by GET /api/jobs. */
public record JobSummary(
        UUID id,
        JobStatus status,
        int documentCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static JobSummary from(Job job) {
        return new JobSummary(job.id(), job.status(), job.documentCount(), job.failureReason(),
                job.createdAt(), job.updatedAt());
    }
}
