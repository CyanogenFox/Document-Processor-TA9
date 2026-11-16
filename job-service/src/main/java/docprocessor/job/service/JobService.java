package docprocessor.job.service;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.PageResult;
import java.util.List;
import java.util.UUID;

/** Creates, looks up, lists and deletes jobs. */
public interface JobService {

    // Throws InvalidJobException for duplicate document ids.
    Job create(List<DocumentInput> documents);

    // Throws JobNotFoundException for an unknown id.
    Job get(UUID id);

    // Newest first; a null status means no filter; the size is capped at 100.
    PageResult<Job> list(JobStatus statusOrNull, int page, int size);

    // Throws JobNotFoundException for an unknown id, JobActiveException while the job is unfinished.
    void delete(UUID id);
}
