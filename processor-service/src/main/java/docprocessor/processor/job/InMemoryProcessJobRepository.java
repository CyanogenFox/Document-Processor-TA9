package docprocessor.processor.job;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Keeps jobs in a concurrent map; everything is lost on restart. */
@Repository
public class InMemoryProcessJobRepository implements ProcessJobRepository {

    private final Map<UUID, ProcessJob> jobs = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(ProcessJob job) {
        return jobs.putIfAbsent(job.jobId(), job) == null;
    }

    @Override
    public void save(ProcessJob job) {
        jobs.put(job.jobId(), job);
    }

    @Override
    public Optional<ProcessJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public boolean deleteById(UUID jobId) {
        return jobs.remove(jobId) != null;
    }
}
