package docprocessor.processor.service;

import docprocessor.processor.config.ProcessorProperties;
import docprocessor.processor.repository.ProcessJobRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically removes finished jobs older than the retention time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinishedJobSweeper {

    private final ProcessJobRepository repository;
    private final ProcessorProperties properties;
    private final Clock clock;

    /** Fixed delay, so runs never overlap; a failure is logged and the next run proceeds. */
    @Scheduled(fixedDelayString = "${processor.sweep-interval:1m}")
    public void sweep() {
        try {
            Instant cutoff = clock.instant().minus(properties.retention());
            int removed = repository.deleteFinishedBefore(cutoff);
            if (removed > 0) {
                log.info("Removed {} finished jobs older than {}", removed, properties.retention());
            }
        } catch (Exception e) {
            log.error("Finished job sweep failed", e);
        }
    }
}
