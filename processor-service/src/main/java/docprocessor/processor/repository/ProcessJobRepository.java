package docprocessor.processor.repository;
import docprocessor.processor.domain.ProcessJob;

import java.util.Optional;
import java.util.UUID;

/** Storage for jobs; the in-memory version can be swapped for a database later. */
public interface ProcessJobRepository {

    /** Stores the job only if its id is new; returns false if it already existed. */
    boolean saveIfAbsent(ProcessJob job);

    void save(ProcessJob job);

    Optional<ProcessJob> findById(UUID jobId);

    /** Returns true if a job was removed. */
    boolean deleteById(UUID jobId);
}
