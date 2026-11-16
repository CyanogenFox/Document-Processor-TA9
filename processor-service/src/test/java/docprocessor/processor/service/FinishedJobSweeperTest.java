package docprocessor.processor.service;

import static org.assertj.core.api.Assertions.assertThat;

import docprocessor.contracts.ProcessStatus;
import docprocessor.processor.config.ProcessorProperties;
import docprocessor.processor.domain.ProcessJob;
import docprocessor.processor.repository.InMemoryProcessJobRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plain unit tests for the sweeper: real repository, fixed clock, sweep called directly. */
class FinishedJobSweeperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration RETENTION = Duration.ofHours(1);

    private final InMemoryProcessJobRepository repository = new InMemoryProcessJobRepository();
    private final ProcessorProperties properties = new ProcessorProperties(10, 20,
            URI.create("ws://localhost:8080/ws/events"), Duration.ofSeconds(1), Duration.ofSeconds(30),
            RETENTION, Duration.ofMinutes(1));
    private final FinishedJobSweeper sweeper =
            new FinishedJobSweeper(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private ProcessJob job(ProcessStatus status, Instant finishedAt) {
        ProcessJob job = new ProcessJob(UUID.randomUUID(), status, null, null, finishedAt);
        repository.save(job);
        return job;
    }

    @Test
    void sweep_finishedOlderThanRetention_removed() {
        ProcessJob completed = job(ProcessStatus.COMPLETED, NOW.minus(RETENTION).minusSeconds(1));
        ProcessJob failed = job(ProcessStatus.FAILED, NOW.minus(Duration.ofDays(1)));

        sweeper.sweep();

        assertThat(repository.findById(completed.jobId())).isEmpty();
        assertThat(repository.findById(failed.jobId())).isEmpty();
    }

    @Test
    void sweep_finishedWithinRetention_kept() {
        ProcessJob recent = job(ProcessStatus.COMPLETED, NOW.minus(RETENTION).plusSeconds(1));
        ProcessJob atCutoff = job(ProcessStatus.FAILED, NOW.minus(RETENTION));

        sweeper.sweep();

        assertThat(repository.findById(recent.jobId())).isPresent();
        assertThat(repository.findById(atCutoff.jobId())).isPresent();
    }

    @Test
    void sweep_activeJob_keptWhateverItsAge() {
        ProcessJob queued = job(ProcessStatus.QUEUED, null);
        ProcessJob processing = job(ProcessStatus.PROCESSING, null);

        sweeper.sweep();

        assertThat(repository.findById(queued.jobId())).isPresent();
        assertThat(repository.findById(processing.jobId())).isPresent();
    }
}
