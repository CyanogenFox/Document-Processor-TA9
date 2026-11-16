package docprocessor.job.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Settings under "job"; every value has a default so no config file is needed. */
@ConfigurationProperties("job")
public record JobProperties(
        @DefaultValue("10MB") DataSize maxRequestSize,
        @DefaultValue Processor processor,
        @DefaultValue Poller poller) {

    /** How to reach the Processor Service. */
    public record Processor(
            @DefaultValue("http://localhost:8081") URI baseUrl,
            @DefaultValue("10") int maxConcurrentDispatches) {
    }

    /** The stale-job poller; thresholds are measured from a job updatedAt. */
    public record Poller(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("5s") Duration interval,
            @DefaultValue("10s") Duration staleAfter,
            @DefaultValue("5m") Duration giveUpAfter) {
    }
}
