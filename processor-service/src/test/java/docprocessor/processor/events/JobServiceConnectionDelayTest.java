package docprocessor.processor.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Checks the reconnect delay doubles and stops at the maximum. */
class JobServiceConnectionDelayTest {

    @Test
    void nextDelay_belowMax_doubles() {
        assertThat(JobServiceConnection.nextDelay(Duration.ofSeconds(1), Duration.ofSeconds(30)))
                .isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void nextDelay_doubleExceedsMax_capsAtMax() {
        assertThat(JobServiceConnection.nextDelay(Duration.ofSeconds(20), Duration.ofSeconds(30)))
                .isEqualTo(Duration.ofSeconds(30));
    }
}
