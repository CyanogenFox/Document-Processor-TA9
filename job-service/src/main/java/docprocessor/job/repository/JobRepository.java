package docprocessor.job.repository;

import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Storage for jobs. */
public interface JobRepository {

    void save(Job job);

    Optional<Job> findById(UUID id);

    /** Atomically replaces the job with the result of {@code change}; empty if the job does not exist. */
    Optional<Job> update(UUID id, UnaryOperator<Job> change);

    /** Newest first; {@code page} is zero-based; a null status means no filter. */
    PageResult<Job> findAll(JobStatus statusOrNull, int page, int size);

    /** Dispatched, unfinished jobs whose updatedAt is strictly before the cutoff. */
    List<Job> findDispatchedUnfinishedUpdatedBefore(Instant cutoff);

    /** Pending jobs the Processor has not accepted yet, whose updatedAt is strictly before the cutoff. */
    List<Job> findUndispatchedPendingUpdatedBefore(Instant cutoff);

    /** Atomically deletes the job only if it is finished; true if it was deleted. */
    boolean deleteIfFinished(UUID id);
}
