package docprocessor.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Job lifecycle event. The reason is for logs only; state comes from REST.
 */
public record JobEvent(UUID jobId, JobEventType type, Instant occurredAt, String reason) {
}
