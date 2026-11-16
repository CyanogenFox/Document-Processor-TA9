package docprocessor.job.domain;

/** Failure reasons given by the Job Service itself (PROCESSING_ERROR arrives from the Processor as text). */
public final class FailureReason {

    public static final String PROCESSOR_UNAVAILABLE = "PROCESSOR_UNAVAILABLE";
    public static final String PROCESSOR_LOST = "PROCESSOR_LOST";
    public static final String PROCESSOR_REJECTED = "PROCESSOR_REJECTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private FailureReason() {
    }
}
