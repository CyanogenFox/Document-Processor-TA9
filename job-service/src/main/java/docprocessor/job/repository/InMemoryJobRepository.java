package docprocessor.job.repository;

import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;

/** Keeps jobs in a concurrent map; everything is lost on restart. */
@Repository
public class InMemoryJobRepository implements JobRepository {

    private static final Comparator<Job> NEWEST_FIRST =
            Comparator.comparing(Job::createdAt).reversed().thenComparing(Job::id);

    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        jobs.put(job.id(), job);
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public Optional<Job> update(UUID id, UnaryOperator<Job> change) {
        return Optional.ofNullable(jobs.computeIfPresent(id, (key, current) -> change.apply(current)));
    }

    @Override
    public PageResult<Job> findAll(JobStatus statusOrNull, int page, int size) {
        List<Job> matching = jobs.values().stream()
                .filter(job -> statusOrNull == null || job.status() == statusOrNull)
                .sorted(NEWEST_FIRST)
                .toList();
        List<Job> content = matching.stream().skip((long) page * size).limit(size).toList();
        return new PageResult<>(content, page, size, matching.size());
    }

    @Override
    public List<Job> findDispatchedUnfinishedUpdatedBefore(Instant cutoff) {
        return jobs.values().stream()
                .filter(job -> job.dispatchedAt() != null)
                .filter(job -> !job.status().isFinished())
                .filter(job -> job.updatedAt().isBefore(cutoff))
                .toList();
    }

    @Override
    public List<Job> findUndispatchedUnfinishedUpdatedBefore(Instant cutoff) {
        return jobs.values().stream()
                .filter(job -> job.dispatchedAt() == null)
                .filter(job -> !job.status().isFinished())
                .filter(job -> job.updatedAt().isBefore(cutoff))
                .toList();
    }

    @Override
    public boolean deleteIfFinished(UUID id) {
        boolean[] removed = {false};
        jobs.computeIfPresent(id, (key, job) -> {
            if (job.status().isFinished()) {
                removed[0] = true;
                return null;
            }
            return job;
        });
        return removed[0];
    }
}
