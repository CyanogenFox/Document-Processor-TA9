package docprocessor.job.service;

import docprocessor.contracts.DocumentResult;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Applies lifecycle changes atomically inside the repository and ignores illegal ones. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLifecycleImpl implements JobLifecycle {

    private final JobRepository repository;
    private final Clock clock;

    @Override
    public boolean accepted(UUID id) {
        return apply(id, "accepted",
                job -> !job.status().isFinished() && job.dispatchedAt() == null,
                job -> job.toBuilder().dispatchedAt(clock.instant()).documents(null).updatedAt(clock.instant()).build());
    }

    @Override
    public boolean started(UUID id) {
        return apply(id, "started",
                job -> job.status().canMoveTo(JobStatus.PROCESSING),
                job -> job.toBuilder().status(JobStatus.PROCESSING).updatedAt(clock.instant()).build());
    }

    @Override
    public boolean completed(UUID id, List<DocumentResult> results) {
        return apply(id, "completed",
                job -> job.status().canMoveTo(JobStatus.COMPLETED),
                job -> job.toBuilder().status(JobStatus.COMPLETED).results(results).documents(null)
                        .updatedAt(clock.instant()).build());
    }

    @Override
    public boolean failed(UUID id, String reason) {
        return apply(id, "failed",
                job -> job.status().canMoveTo(JobStatus.FAILED),
                job -> job.toBuilder().status(JobStatus.FAILED).failureReason(reason).documents(null)
                        .updatedAt(clock.instant()).build());
    }

    /** Runs the change inside the atomic repository update; unknown or disallowed changes leave the job untouched. */
    private boolean apply(UUID id, String change, Predicate<Job> allowed, UnaryOperator<Job> modify) {
        boolean[] applied = {false};
        boolean found = repository.update(id, job -> {
            if (!allowed.test(job)) {
                return job;
            }
            applied[0] = true;
            return modify.apply(job);
        }).isPresent();
        if (!found) {
            log.warn("Ignored {} for job {}: unknown job", change, id);
        } else if (applied[0]) {
            log.info("Applied {} for job {}", change, id);
        } else {
            log.info("Ignored {} for job {}: not allowed in its current state", change, id);
        }
        return applied[0];
    }
}
