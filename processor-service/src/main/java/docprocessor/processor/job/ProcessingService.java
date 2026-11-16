package docprocessor.processor.job;

import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Accepts, looks up and deletes jobs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingService {

    /** The stored job, and whether this call created it (false for a repeated jobId). */
    public record Submission(ProcessJob job, boolean created) {
    }

    private final ProcessJobRepository repository;
    private final JobQueue queue;
    private final JobWorker worker;

    public Submission submit(ProcessRequest request) {
        ProcessJob queued = new ProcessJob(request.jobId(), ProcessStatus.QUEUED, null, null);
        if (!repository.saveIfAbsent(queued)) {
            return new Submission(get(request.jobId()), false);
        }
        try {
            queue.submit(() -> worker.run(request));
        } catch (QueueFullException e) {
            repository.deleteById(request.jobId());
            log.warn("Queue full, rejected job {}", request.jobId());
            throw e;
        }
        log.info("Job {} accepted", request.jobId());
        return new Submission(queued, true);
    }

    public ProcessJob get(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new ProcessJobNotFoundException("Job not found: " + jobId));
    }

    public void delete(UUID jobId) {
        ProcessJob job = get(jobId);
        if (job.status() == ProcessStatus.QUEUED || job.status() == ProcessStatus.PROCESSING) {
            throw new ProcessJobActiveException("Job is still active: " + jobId);
        }
        repository.deleteById(jobId);
    }
}
