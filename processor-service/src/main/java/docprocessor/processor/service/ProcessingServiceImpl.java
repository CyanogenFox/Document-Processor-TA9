package docprocessor.processor.service;
import docprocessor.processor.repository.ProcessJobRepository;
import docprocessor.processor.domain.ProcessJob;

import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Default ProcessingService: stores jobs in the repository and runs them on the queue. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingServiceImpl implements ProcessingService {

    private final ProcessJobRepository repository;
    private final JobQueue queue;
    private final JobWorker worker;

    @Override
    public Submission submit(ProcessRequest request) {
        ProcessJob queued = new ProcessJob(request.jobId(), ProcessStatus.QUEUED, null, null, null);
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

    @Override
    public ProcessJob get(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new ProcessJobNotFoundException("Job not found: " + jobId));
    }

    @Override
    public void delete(UUID jobId) {
        ProcessJob job = get(jobId);
        if (job.status() == ProcessStatus.QUEUED || job.status() == ProcessStatus.PROCESSING) {
            throw new ProcessJobActiveException("Job is still active: " + jobId);
        }
        repository.deleteById(jobId);
    }
}
