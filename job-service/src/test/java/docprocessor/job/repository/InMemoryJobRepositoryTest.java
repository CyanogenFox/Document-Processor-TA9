package docprocessor.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryJobRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final JobRepository repository = new InMemoryJobRepository();

    private Job job(JobStatus status, Instant createdAt) {
        return Job.builder().id(UUID.randomUUID()).status(status).createdAt(createdAt).updatedAt(createdAt).build();
    }

    @Test
    void findAll_newestFirstAndPaged() {
        Job[] jobs = new Job[5];
        for (int i = 0; i < 5; i++) {
            jobs[i] = job(JobStatus.PENDING, T0.plusSeconds(i));
            repository.save(jobs[i]);
        }

        PageResult<Job> page = repository.findAll(null, 1, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.content()).extracting(Job::id).containsExactly(jobs[2].id(), jobs[1].id());
    }

    @Test
    void findAll_statusFilter_onlyMatching() {
        repository.save(job(JobStatus.PENDING, T0));
        Job done = job(JobStatus.COMPLETED, T0.plusSeconds(1));
        repository.save(done);

        PageResult<Job> page = repository.findAll(JobStatus.COMPLETED, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).containsExactly(done);
    }

    @Test
    void update_isAtomic() throws Exception {
        Job j = job(JobStatus.PENDING, T0).toBuilder().documentCount(0).build();
        repository.save(j);
        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                go.await();
                return repository.update(j.id(), x -> x.toBuilder().documentCount(x.documentCount() + 1).build());
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(repository.findById(j.id()).orElseThrow().documentCount()).isEqualTo(100);
    }

    @Test
    void deleteIfFinished_activeJob_false() {
        Job j = job(JobStatus.PROCESSING, T0);
        repository.save(j);

        assertThat(repository.deleteIfFinished(j.id())).isFalse();
        assertThat(repository.findById(j.id())).isPresent();
    }

    @Test
    void deleteIfFinished_finishedJob_true() {
        Job j = job(JobStatus.FAILED, T0);
        repository.save(j);

        assertThat(repository.deleteIfFinished(j.id())).isTrue();
        assertThat(repository.findById(j.id())).isEmpty();
    }

    @Test
    void findDispatchedUnfinishedUpdatedBefore_skipsUndispatchedAndFinished() {
        Instant cutoff = T0.plusSeconds(100);
        Job stale = job(JobStatus.PROCESSING, T0).toBuilder().dispatchedAt(T0).build();
        Job undispatched = job(JobStatus.PENDING, T0);
        Job finished = job(JobStatus.COMPLETED, T0).toBuilder().dispatchedAt(T0).build();
        Job fresh = job(JobStatus.PROCESSING, T0).toBuilder().dispatchedAt(T0).updatedAt(cutoff).build();
        for (Job j : new Job[] {stale, undispatched, finished, fresh}) {
            repository.save(j);
        }

        assertThat(repository.findDispatchedUnfinishedUpdatedBefore(cutoff)).containsExactly(stale);
    }
}
