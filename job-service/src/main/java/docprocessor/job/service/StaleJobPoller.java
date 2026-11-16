package docprocessor.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically asks JobSync to recover jobs whose Processor events were missed. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "job.poller.enabled", havingValue = "true", matchIfMissing = true)
public class StaleJobPoller {

    private final JobSync jobSync;

    /** Fixed delay, so runs never overlap; failures are logged and the next run proceeds. */
    @Scheduled(fixedDelayString = "${job.poller.interval:5s}")
    public void poll() {
        try {
            jobSync.syncStale();
        } catch (Throwable t) {
            log.error("Stale job poll failed", t);
        }
    }
}
