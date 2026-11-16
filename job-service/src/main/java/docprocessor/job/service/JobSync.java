package docprocessor.job.service;

import java.util.UUID;

/** Brings a job in line with the Processor's view of it. */
public interface JobSync {

    /** Fetches the Processor's state for the job and applies it; never throws for Processor problems. */
    void sync(UUID jobId);
}
