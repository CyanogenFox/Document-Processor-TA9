package docprocessor.processor.service;

import docprocessor.contracts.ProcessRequest;
import docprocessor.processor.domain.ProcessJob;
import java.util.UUID;

/** Accepts, looks up and deletes jobs. */
public interface ProcessingService {

    /** The stored job, and whether this call created it (false for a repeated jobId). */
    record Submission(ProcessJob job, boolean created) {
    }

    // Throws QueueFullException when the queue has no room.
    Submission submit(ProcessRequest request);

    // Throws ProcessJobNotFoundException for an unknown jobId.
    ProcessJob get(UUID jobId);

    // Throws ProcessJobNotFoundException for an unknown jobId, ProcessJobActiveException while the job is running.
    void delete(UUID jobId);
}
