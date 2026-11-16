package docprocessor.processor.events;

import static org.assertj.core.api.Assertions.assertThatCode;

import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import docprocessor.processor.config.ProcessorProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;
import tools.jackson.databind.json.JsonMapper;

/** Checks the publisher is safe to call when the Job Service is unreachable. */
class WebSocketJobEventPublisherNotConnectedTest {

    @Test
    void publish_notConnected_doesNotThrow() {
        URI closedPort = URI.create("ws://localhost:" + TestSocketUtils.findAvailableTcpPort() + "/ws/events");
        ProcessorProperties properties =
                new ProcessorProperties(10, 20, closedPort, Duration.ofMillis(100), Duration.ofSeconds(1),
                Duration.ofHours(1), Duration.ofMinutes(1));
        JobServiceConnection connection = new JobServiceConnection(properties);
        WebSocketJobEventPublisher publisher = new WebSocketJobEventPublisher(connection, JsonMapper.builder().build());
        JobEvent event = new JobEvent(UUID.randomUUID(), JobEventType.FAILED, Instant.now(), "boom");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }
}
