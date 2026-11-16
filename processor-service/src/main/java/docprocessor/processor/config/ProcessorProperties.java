package docprocessor.processor.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under "processor"; every value has a default so no config file is needed. */
@ConfigurationProperties("processor")
public record ProcessorProperties(
        @DefaultValue("10") int workers,
        @DefaultValue("20") int queueCapacity,
        @DefaultValue("ws://localhost:8080/ws/events") URI jobServiceWsUrl,
        @DefaultValue("1s") Duration reconnectDelay,
        @DefaultValue("30s") Duration maxReconnectDelay,
        @DefaultValue("1h") Duration retention,
        @DefaultValue("1m") Duration sweepInterval) {
}
