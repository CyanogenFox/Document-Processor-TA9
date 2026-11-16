package docprocessor.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.InMemoryJobRepository;
import docprocessor.job.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobLifecycleTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T00:05:00Z");

    private JobRepository repository;
    private JobLifecycle lifecycle;
    private UUID id;

    @BeforeEach
    void setUp() {
        repository = new InMemoryJobRepository();
        lifecycle = new JobLifecycleImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        id = UUID.randomUUID();
        repository.save(Job.builder().id(id).status(JobStatus.PENDING)
                .documents(List.of(new DocumentInput("a", "text"))).documentCount(1)
                .createdAt(CREATED).updatedAt(CREATED).build());
    }

    private Job job() {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void started_pendingJob_movesToProcessing() {
        assertThat(lifecycle.started(id)).isTrue();

        assertThat(job().status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void started_twice_secondIgnored() {
        lifecycle.started(id);
        Job before = job();

        assertThat(lifecycle.started(id)).isFalse();
        assertThat(job()).isEqualTo(before);
    }

    @Test
    void completed_setsResultsAndDropsDocuments() {
        List<DocumentResult> results = List.of(new DocumentResult("a", 1, 1, List.of(), "h", 4, 0L));

        assertThat(lifecycle.completed(id, results)).isTrue();

        assertThat(job().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job().results()).isEqualTo(results);
        assertThat(job().documents()).isNull();
    }

    @Test
    void failed_afterCompleted_ignored() {
        lifecycle.completed(id, List.of());

        assertThat(lifecycle.failed(id, "X")).isFalse();
        assertThat(job().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job().failureReason()).isNull();
    }

    @Test
    void accepted_setsDispatchedAtAndDropsDocuments() {
        assertThat(lifecycle.accepted(id)).isTrue();

        assertThat(job().dispatchedAt()).isEqualTo(NOW);
        assertThat(job().documents()).isNull();
        assertThat(job().status()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void accepted_afterCompleted_keepsCompleted() {
        lifecycle.completed(id, List.of());

        assertThat(lifecycle.accepted(id)).isFalse();
        assertThat(job().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job().dispatchedAt()).isNull();
    }

    @Test
    void anyChange_unknownJob_false() {
        UUID other = UUID.randomUUID();

        assertThat(lifecycle.accepted(other)).isFalse();
        assertThat(lifecycle.started(other)).isFalse();
        assertThat(lifecycle.completed(other, List.of())).isFalse();
        assertThat(lifecycle.failed(other, "X")).isFalse();
    }
}
