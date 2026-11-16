package docprocessor.job.processor;

/** The Processor answered with a 4xx; retrying will not help. */
public class ProcessorRejectedException extends RuntimeException {

    public ProcessorRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
