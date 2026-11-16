package docprocessor.contracts;

/** Lifecycle status of a processing job. */
public enum ProcessStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}
