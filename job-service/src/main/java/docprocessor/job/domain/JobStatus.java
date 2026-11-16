package docprocessor.job.domain;

/** Lifecycle state of a job; only forward moves are allowed. */
public enum JobStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;

    /** True if a job in this state may move to {@code next}. */
    public boolean canMoveTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING || next == COMPLETED || next == FAILED;
            case PROCESSING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
