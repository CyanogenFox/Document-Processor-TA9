package docprocessor.job.service;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import docprocessor.job.repository.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Default JobService: stores jobs in the repository and hands new ones to the dispatcher. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobRepository repository;
    private final JobDispatcher dispatcher;
    private final Clock clock;

    @Override
    public Job create(List<DocumentInput> documents) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (DocumentInput document : documents) {
            if (!seen.add(document.id())) {
                duplicates.add(document.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new InvalidJobException("Duplicate document ids: " + String.join(", ", duplicates));
        }
        Instant now = clock.instant();
        Job job = Job.builder()
                .id(UUID.randomUUID())
                .status(JobStatus.PENDING)
                .documents(documents)
                .documentCount(documents.size())
                .createdAt(now)
                .updatedAt(now)
                .build();
        repository.save(job);
        log.info("Job {} created with {} documents", job.id(), job.documentCount());
        dispatcher.dispatch(job);
        return job;
    }

    @Override
    public Job get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
    }

    @Override
    public PageResult<Job> list(JobStatus statusOrNull, int page, int size) {
        return repository.findAll(statusOrNull, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @Override
    public void delete(UUID id) {
        get(id);
        if (!repository.deleteIfFinished(id)) {
            throw new JobActiveException("Job is still active: " + id);
        }
    }
}
