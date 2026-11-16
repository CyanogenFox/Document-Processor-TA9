package docprocessor.job.domain;

import static docprocessor.job.domain.JobStatus.COMPLETED;
import static docprocessor.job.domain.JobStatus.FAILED;
import static docprocessor.job.domain.JobStatus.PENDING;
import static docprocessor.job.domain.JobStatus.PROCESSING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStatusTest {

    @Test
    void canMoveTo_forwardMoves_true() {
        assertThat(PENDING.canMoveTo(PROCESSING)).isTrue();
        assertThat(PENDING.canMoveTo(COMPLETED)).isTrue();
        assertThat(PENDING.canMoveTo(FAILED)).isTrue();
        assertThat(PROCESSING.canMoveTo(COMPLETED)).isTrue();
        assertThat(PROCESSING.canMoveTo(FAILED)).isTrue();
    }

    @Test
    void canMoveTo_backwardOrFromFinished_false() {
        assertThat(PROCESSING.canMoveTo(PENDING)).isFalse();
        assertThat(COMPLETED.canMoveTo(FAILED)).isFalse();
        assertThat(FAILED.canMoveTo(COMPLETED)).isFalse();
        for (JobStatus s : JobStatus.values()) {
            assertThat(s.canMoveTo(s)).isFalse();
        }
    }
}
