package docprocessor.job.service;

import docprocessor.contracts.ProcessResponse;
import docprocessor.job.domain.FailureReason;
import docprocessor.job.domain.Job;
import docprocessor.job.config.JobProperties;
import docprocessor.job.processor.ProcessorClient;
import docprocessor.job.processor.ProcessorRejectedException;
import docprocessor.job.processor.ProcessorUnavailableException;
import docprocessor.job.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Reads the Processor's state for a job and applies it through the lifecycle. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSyncImpl implements JobSync {

    private final JobRepository repository;
    private final JobLifecycle lifecycle;
    private final ProcessorClient client;
    private final Clock clock;
    private final JobProperties properties;

    @Override
    public void sync(UUID jobId) {
        Optional<Job> job = repository.findById(jobId);
        if (job.isEmpty() || job.get().status().isFinished()) {
            return;
        }
        Optional<ProcessResponse> response;
        try {
            response = client.find(jobId);
        } catch (ProcessorUnavailableException e) {
            giveUpIfTooOld(job.get());
            return;
        } catch (ProcessorRejectedException e) {
            log.warn("Sync of job {} skipped: Processor rejected the lookup", jobId);
            return;
        }
        if (response.isEmpty()) {
            lifecycle.failed(jobId, FailureReason.PROCESSOR_LOST);
            return;
        }
        apply(jobId, response.get());
    }

    @Override
    public void syncStale() {
        Instant cutoff = clock.instant().minus(properties.poller().staleAfter());
        for (Job job : repository.findDispatchedUnfinishedUpdatedBefore(cutoff)) {
            try {
                sync(job.id());
            } catch (RuntimeException e) {
                log.error("Sync of job {} failed unexpectedly", job.id(), e);
            }
        }
    }

    /** Measured from the job's last change, not from the first failed poll (known weak point). */
    private void giveUpIfTooOld(Job job) {
        if (job.updatedAt().isBefore(clock.instant().minus(properties.poller().giveUpAfter()))) {
            log.warn("Job {} failed: Processor unavailable past the give-up time", job.id());
            lifecycle.failed(job.id(), FailureReason.PROCESSOR_UNAVAILABLE);
        } else {
            log.warn("Sync of job {} skipped: Processor unavailable", job.id());
        }
    }

    private void apply(UUID jobId, ProcessResponse response) {
        switch (response.status()) {
            case QUEUED -> log.debug("Job {} is still queued at the Processor", jobId);
            case PROCESSING -> lifecycle.started(jobId);
            case COMPLETED -> {
                lifecycle.completed(jobId, response.results());
                deleteQuietly(jobId);
            }
            case FAILED -> {
                lifecycle.failed(jobId, response.failureReason());
                deleteQuietly(jobId);
            }
        }
    }

    /** The record stays at the Processor if this fails (known limit; expiry cleans up later). */
    private void deleteQuietly(UUID jobId) {
        try {
            client.delete(jobId);
        } catch (RuntimeException e) {
            log.warn("Could not delete job {} at the Processor: {}", jobId, e.toString());
        }
    }
}
